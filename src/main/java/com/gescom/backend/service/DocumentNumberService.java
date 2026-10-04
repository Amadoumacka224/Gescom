package com.gescom.backend.service;

import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.function.Function;

/**
 * Génération des numéros des documents (commandes, factures, livraisons, retours).
 *
 * <h2>Ce qu'on remplace</h2>
 *
 * Avant, les numéros étaient un préfixe + {@code System.currentTimeMillis()} :
 * {@code FACT-1786647700109}. Ça posait quatre problèmes, le premier étant légal :
 *
 * <ol>
 *   <li>une facture doit avoir un numéro qui se SUIT, sans trou. Un horodatage ne fait ni
 *       l'un ni l'autre : impossible de vérifier qu'aucune facture ne manque ;</li>
 *   <li>le compteur était GLOBAL : deux entreprises partageaient la même suite, donc chacune
 *       avait des trous dans la sienne ;</li>
 *   <li>deux documents créés dans la même milliseconde avaient le même numéro. Ce n'est pas
 *       que de la théorie : en créant six commandes à la suite, on tombe sur
 *       {@code uq_orders_company_number} (vu en écrivant les tests du tableau de bord) ;</li>
 *   <li>un numéro de treize chiffres est illisible et impossible à donner au téléphone.</li>
 * </ol>
 *
 * Maintenant le format est {@code FACT-2026-000042}.
 *
 * <h2>Sans table de compteurs</h2>
 *
 * Le dernier numéro est lu directement dans la table du document, avec un {@code MAX} sur
 * le préfixe et l'année. Rien de nouveau en base : la suite vient de ce qui existe déjà, donc
 * pas de compteur à garder synchronisé avec les documents. C'est le document qui compte.
 *
 * Ça a deux conséquences, une bonne et une à connaître :
 *
 * <ul>
 *   <li>si on supprime le dernier document de l'année, son numéro sera réutilisé. Pour une
 *       facture c'est même ce qu'on veut (la suite doit rester sans trou), et de toute façon
 *       on ne peut plus supprimer une facture dès qu'il y a eu un paiement ;</li>
 *   <li>le {@code MAX} fait une lecture qui utilise un index mais grossit avec le temps. Pour
 *       un back-office, avec l'index sur le numéro, ce n'est rien ; avec des millions de
 *       documents par an, une table de compteurs redeviendrait la bonne solution.</li>
 * </ul>
 *
 * <h2>Accès en même temps</h2>
 *
 * Si deux transactions lisaient le {@code MAX} en même temps, elles auraient la même valeur.
 * On les fait donc passer l'une après l'autre en verrouillant une ligne qui existe déjà :
 * celle de l'ENTREPRISE, lue en {@code SELECT ... FOR UPDATE} avant le calcul. La deuxième
 * vente attend que la première ait fini, et lit alors le bon maximum.
 *
 * L'inconvénient : les créations de documents d'une même entreprise passent l'une après
 * l'autre. Sur un back-office avec quelques postes, ça ne se voit pas. Les entreprises entre
 * elles ne se bloquent pas, chacune verrouille sa propre ligne.
 */
@Service
public class DocumentNumberService {

    /** Préfixes des documents. */
    public enum DocumentType {
        ORDER("CMD"), INVOICE("FACT"), DELIVERY("LIV"), RETURN("RET");

        private final String prefix;

        DocumentType(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    /**
     * Quatre chiffres, et on ne peut pas choisir n'importe quelle longueur.
     *
     * Le maximum est calculé sur la CHAÎNE : sans les zéros devant, l'ordre alphabétique
     * mettrait 9 après 10 et la suite repartirait en arrière. Surtout, la longueur doit être
     * celle DÉJÀ EN BASE : les données de départ sont au format {@code CMD-2026-0034}. Avec six
     * chiffres, on aurait deux longueurs mélangées, et {@code CMD-2026-0034} resterait toujours
     * plus grand que {@code CMD-2026-000035} : le compteur n'avancerait jamais.
     *
     * On l'a vu en le lançant, pas en théorie : le premier essai avec six chiffres a donné
     * deux fois {@code CMD-2026-000001}.
     *
     * Limite : 9 999 documents par an et par entreprise. Au-delà, il faudrait agrandir le
     * format ET renuméroter les documents existants de l'année.
     */
    private static final String NUMBER_FORMAT = "%s-%d-%04d";

    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final DeliveryRepository deliveryRepository;
    private final StockReturnRepository stockReturnRepository;

    public DocumentNumberService(CompanyRepository companyRepository, OrderRepository orderRepository,
                                 InvoiceRepository invoiceRepository, DeliveryRepository deliveryRepository,
                                 StockReturnRepository stockReturnRepository) {
        this.companyRepository = companyRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.deliveryRepository = deliveryRepository;
        this.stockReturnRepository = stockReturnRepository;
    }

    /**
     * Donne le numéro suivant pour ce type de document, dans l'entreprise en cours.
     *
     * {@code MANDATORY} : cette méthode DOIT tourner dans la transaction de l'appelant. Avec sa
     * propre transaction, le verrou serait libéré à son commit, avant même que le document soit
     * enregistré, et deux ventes en même temps pourraient avoir le même numéro (justement ce
     * qu'on veut éviter).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            // Sans entreprise, on ne sait pas quelle suite utiliser. Ça ne devrait pas arriver
            // (toute écriture passe par un utilisateur qui a une entreprise), mais si ça arrive
            // on veut le voir tout de suite plutôt que de générer un numéro qui ne veut rien dire.
            throw BusinessException.of("document.number.noCompany",
                    "Impossible d'attribuer un numéro de document hors du contexte d'une entreprise");
        }

        // C'est ici que tout se joue. Le verrou est libéré au commit de l'appelant, donc APRÈS
        // l'enregistrement du document : le suivant verra bien ce numéro.
        companyRepository.findByIdForUpdate(companyId);

        int year = Year.now().getValue();
        String pattern = type.prefix() + "-" + year + "-%";
        long next = parseCounter(maxNumberOf(type, pattern)) + 1;

        return String.format(NUMBER_FORMAT, type.prefix(), year, next);
    }

    private String maxNumberOf(DocumentType type, String pattern) {
        Function<String, String> query = switch (type) {
            case ORDER -> orderRepository::findMaxNumber;
            case INVOICE -> invoiceRepository::findMaxNumber;
            case DELIVERY -> deliveryRepository::findMaxNumber;
            case RETURN -> stockReturnRepository::findMaxNumber;
        };
        return query.apply(pattern);
    }

    /**
     * Récupère le compteur d'un numéro {@code PREFIXE-ANNEE-NNNN}, ou 0 s'il n'y en a pas encore.
     *
     * On coupe sur le dernier tiret plutôt qu'à une position fixe : comme ça on lit le
     * compteur quelle que soit sa longueur, et si on passait un jour à cinq chiffres il n'y
     * aurait rien à changer ici.
     *
     * Les anciens numéros ({@code FACT-} suivi d'un horodatage) n'arrivent jamais ici : le
     * {@code LIKE 'FACT-2026-%'} de la requête les exclut, car un horodatage en millisecondes
     * commence par 17 et pas par l'année.
     *
     * On reste tolérant : une vieille ligne qui ressemble au motif sans avoir le bon format ne
     * doit pas empêcher d'enregistrer une vente. Repartir de 0 ne pose pas de problème, la
     * contrainte d'unicité est là en dernier recours et l'erreur serait claire.
     */
    private long parseCounter(String maxNumber) {
        if (maxNumber == null) {
            return 0L;
        }
        int lastDash = maxNumber.lastIndexOf('-');
        if (lastDash < 0 || lastDash == maxNumber.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(maxNumber.substring(lastDash + 1));
        } catch (NumberFormatException malformed) {
            return 0L;
        }
    }
}

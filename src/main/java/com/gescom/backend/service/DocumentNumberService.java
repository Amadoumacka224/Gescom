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
 * Attribution des numéros de documents commerciaux.
 *
 * <h2>Ce qui est remplacé</h2>
 *
 * Les numéros étaient composés d'un préfixe et de {@code System.currentTimeMillis()} :
 * {@code FACT-1786647700109}. Quatre défauts, dont le premier touche à la conformité du produit :
 *
 * <ol>
 *   <li>une facture doit porter un numéro SÉQUENTIEL et continu. Un horodatage n'est ni l'un ni
 *       l'autre : rien ne permet de constater qu'aucune facture ne manque ;</li>
 *   <li>le compteur était GLOBAL — deux entreprises clientes se partageaient la même suite, et
 *       chacune voyait donc la sienne trouée des documents de l'autre ;</li>
 *   <li>deux documents créés dans la même milliseconde recevaient le même numéro. Ce n'est pas
 *       théorique : six commandes créées d'affilée font sauter {@code uq_orders_company_number},
 *       constaté en écrivant les tests du tableau de bord ;</li>
 *   <li>un numéro à treize chiffres est illisible et incommunicable au téléphone.</li>
 * </ol>
 *
 * La forme devient {@code FACT-2026-000042}.
 *
 * <h2>Sans table de compteurs</h2>
 *
 * Le dernier numéro est lu sur la table du document elle-même, par un {@code MAX} sur le
 * préfixe et l'année. Aucune structure nouvelle : la suite est déduite de ce qui existe, et il
 * n'y a donc aucun compteur à maintenir en cohérence avec les documents — c'est le document qui
 * fait foi.
 *
 * Deux conséquences de ce choix, l'une bonne et l'autre à connaître :
 *
 * <ul>
 *   <li>supprimer le dernier document de l'année libère son numéro, qui sera réattribué. Sur une
 *       facture c'est même souhaitable — la suite doit rester continue —, et la suppression est
 *       de toute façon désormais interdite dès qu'un encaissement existe ;</li>
 *   <li>le {@code MAX} coûte une lecture indexable mais croissante. À l'échelle d'un
 *       back-office, l'index sur le numéro le rend négligeable ; sur des millions de documents
 *       par an, une table de compteurs redeviendrait le bon outil.</li>
 * </ul>
 *
 * <h2>Sûreté face à la concurrence</h2>
 *
 * Un {@code MAX} lu par deux transactions simultanées rendrait deux fois la même valeur. La
 * sérialisation s'appuie donc sur une ligne qui existe déjà : celle de l'ENTREPRISE, lue en
 * {@code SELECT ... FOR UPDATE} avant le calcul. La seconde vente attend que la première ait
 * validé, et lit alors un maximum à jour.
 *
 * Le prix est assumé : les créations de documents d'une même entreprise se suivent au lieu de se
 * croiser. Sur un back-office où quelques postes saisissent en parallèle, cela ne se voit pas.
 * Les entreprises restent indépendantes — chacune verrouille sa propre ligne.
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
     * Quatre chiffres, et la largeur n'est pas libre.
     *
     * Le maximum est calculé sur la CHAÎNE : sans remplissage, l'ordre lexicographique placerait
     * 9 après 10 et la suite repartirait en arrière. Mais surtout, la largeur doit être celle
     * DÉJÀ EN BASE — les données de départ numérotent en {@code CMD-2026-0034}. Émettre sur six
     * chiffres ferait cohabiter deux largeurs, et {@code CMD-2026-0034} resterait éternellement
     * supérieur à {@code CMD-2026-000035} : le compteur ne repartirait jamais.
     *
     * Constaté à l'exécution, et non déduit : la première tentative sur six chiffres a produit
     * deux fois {@code CMD-2026-000001}.
     *
     * Contrepartie : 9 999 documents par an et par entreprise. Au-delà, il faudrait élargir le
     * format ET réécrire les numéros existants d'un même exercice.
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
     * Attribue le numéro suivant pour ce type de document, dans l'entreprise du contexte.
     *
     * {@code MANDATORY} : cette méthode DOIT s'exécuter dans la transaction de l'appelant. Une
     * transaction propre libérerait le verrou dès son commit, avant même que le document ne soit
     * écrit — deux ventes simultanées pourraient alors obtenir le même numéro, ce que tout ceci
     * vise précisément à empêcher.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            // Sans entreprise, la suite n'a pas de périmètre. Le cas ne devrait pas se produire —
            // toute écriture métier passe par un utilisateur rattaché à une entreprise — mais il
            // doit se voir immédiatement plutôt que de produire un numéro hors-sol.
            throw BusinessException.of("document.number.noCompany",
                    "Impossible d'attribuer un numéro de document hors du contexte d'une entreprise");
        }

        // Point de sérialisation : tout se joue ici. Le verrou est relâché au commit de
        // l'appelant, donc APRÈS l'écriture du document — le prochain lecteur verra ce numéro.
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
     * Extrait le compteur d'un numéro {@code PREFIXE-ANNEE-NNNN}, ou 0 s'il n'y en a pas encore.
     *
     * Découpe sur le dernier tiret plutôt que sur une longueur fixe : le compteur reste lisible
     * quelle que soit sa largeur, et une reprise des données sur cinq chiffres n'exigerait pas
     * de retoucher ici.
     *
     * Les numéros à l'ancien format ({@code FACT-} suivi d'un horodatage) n'arrivent jamais
     * jusqu'ici : le motif {@code LIKE 'FACT-2026-%'} de la requête les écarte, un horodatage en
     * millisecondes commençant par 17 et non par l'année.
     *
     * Tolérant plutôt que brutal : une ligne héritée qui ressemblerait au motif sans en avoir la
     * structure ne doit pas empêcher d'enregistrer une vente. Repartir de 0 est sans danger — la
     * contrainte d'unicité reste le dernier mot, et l'échec serait franc.
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

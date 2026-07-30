package com.gescom.backend.service;

import com.gescom.backend.dto.settings.SettingsRequest;
import com.gescom.backend.entity.Settings;
import com.gescom.backend.repository.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service des paramètres globaux de l'application (entreprise, TVA, facturation, thème…).
 * Il n'existe qu'une seule ligne de paramètres en base : getSettings() la retourne, ou la
 * crée avec des valeurs par défaut au tout premier accès (pattern singleton persistant).
 */
@Service
@Transactional
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /** Retourne l'unique enregistrement de paramètres, en l'initialisant par défaut s'il n'existe pas encore. */
    public Settings getSettings() {
        Optional<Settings> settings = settingsRepository.findFirstByOrderByIdAsc();

        if (settings.isPresent()) {
            return settings.get();
        } else {
            // Premier démarrage : on persiste un jeu de paramètres par défaut.
            Settings defaultSettings = new Settings();
            defaultSettings.setCompanyName("GESCOM");
            defaultSettings.setCompanyCountry("Belgique");
            defaultSettings.setLanguage("fr");
            defaultSettings.setCurrency("EUR");
            defaultSettings.setTimezone("Europe/Brussels");
            defaultSettings.setDateFormat("DD/MM/YYYY");
            defaultSettings.setTaxRate(21.0);
            defaultSettings.setInvoicePrefix("INV");
            defaultSettings.setInvoiceNumberStart(1000);
            defaultSettings.setPaymentTerms(30);
            defaultSettings.setFooterText("Merci pour votre confiance");
            defaultSettings.setNotifications(true);
            defaultSettings.setEmailNotifications(true);
            defaultSettings.setOrderNotifications(true);
            defaultSettings.setStockAlerts(true);
            defaultSettings.setLowStockThreshold(10);
            defaultSettings.setTheme("light");

            return settingsRepository.save(defaultSettings);
        }
    }

    public Settings updateSettings(SettingsRequest request) {
        Settings existingSettings = getSettings();
        existingSettings.setLanguage(request.language());
        existingSettings.setCurrency(request.currency());
        existingSettings.setTimezone(request.timezone());
        existingSettings.setDateFormat(request.dateFormat());

        existingSettings.setCompanyName(request.companyName());
        existingSettings.setCompanyEmail(request.companyEmail());
        existingSettings.setCompanyPhone(request.companyPhone());
        existingSettings.setCompanyAddress(request.companyAddress());
        existingSettings.setCompanyCity(request.companyCity());
        existingSettings.setCompanyPostalCode(request.companyPostalCode());
        existingSettings.setCompanyCountry(request.companyCountry());
        existingSettings.setCompanyTaxId(request.companyTaxId());
        existingSettings.setCompanyIban(request.companyIban());
        existingSettings.setCompanyBic(request.companyBic());

        existingSettings.setTaxRate(request.taxRate());
        existingSettings.setInvoicePrefix(request.invoicePrefix());
        existingSettings.setInvoiceNumberStart(request.invoiceNumberStart());
        existingSettings.setPaymentTerms(request.paymentTerms());
        existingSettings.setFooterText(request.footerText());

        existingSettings.setNotifications(request.notifications());
        existingSettings.setEmailNotifications(request.emailNotifications());
        existingSettings.setOrderNotifications(request.orderNotifications());
        existingSettings.setStockAlerts(request.stockAlerts());
        existingSettings.setLowStockThreshold(request.lowStockThreshold());

        existingSettings.setTheme(request.theme());

        return settingsRepository.save(existingSettings);
    }
}

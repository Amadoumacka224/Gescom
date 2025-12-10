package com.gescom.backend.service;

import com.gescom.backend.model.Settings;
import com.gescom.backend.repository.SettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SettingsService {

    @Autowired
    private SettingsRepository settingsRepository;

    public Settings getSettings() {
        Optional<Settings> settings = settingsRepository.findFirstByOrderByIdAsc();

        if (settings.isPresent()) {
            return settings.get();
        } else {
            // Create default settings if none exist
            Settings defaultSettings = new Settings();
            defaultSettings.setCompanyName("GESCOM");
            defaultSettings.setLanguage("fr");
            defaultSettings.setCurrency("EUR");
            defaultSettings.setTimezone("Europe/Paris");
            defaultSettings.setDateFormat("DD/MM/YYYY");
            defaultSettings.setTaxRate(20.0);
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

    public Settings updateSettings(Settings settings) {
        Settings existingSettings = getSettings();
        existingSettings.setLanguage(settings.getLanguage());
        existingSettings.setCurrency(settings.getCurrency());
        existingSettings.setTimezone(settings.getTimezone());
        existingSettings.setDateFormat(settings.getDateFormat());

        existingSettings.setCompanyName(settings.getCompanyName());
        existingSettings.setCompanyEmail(settings.getCompanyEmail());
        existingSettings.setCompanyPhone(settings.getCompanyPhone());
        existingSettings.setCompanyAddress(settings.getCompanyAddress());
        existingSettings.setCompanyCity(settings.getCompanyCity());
        existingSettings.setCompanyPostalCode(settings.getCompanyPostalCode());
        existingSettings.setCompanyCountry(settings.getCompanyCountry());
        existingSettings.setCompanyTaxId(settings.getCompanyTaxId());

        existingSettings.setTaxRate(settings.getTaxRate());
        existingSettings.setInvoicePrefix(settings.getInvoicePrefix());
        existingSettings.setInvoiceNumberStart(settings.getInvoiceNumberStart());
        existingSettings.setPaymentTerms(settings.getPaymentTerms());
        existingSettings.setFooterText(settings.getFooterText());

        existingSettings.setNotifications(settings.getNotifications());
        existingSettings.setEmailNotifications(settings.getEmailNotifications());
        existingSettings.setOrderNotifications(settings.getOrderNotifications());
        existingSettings.setStockAlerts(settings.getStockAlerts());
        existingSettings.setLowStockThreshold(settings.getLowStockThreshold());

        existingSettings.setTheme(settings.getTheme());

        return settingsRepository.save(existingSettings);
    }
}

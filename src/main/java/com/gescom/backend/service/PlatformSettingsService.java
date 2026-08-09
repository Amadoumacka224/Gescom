package com.gescom.backend.service;

import com.gescom.backend.dto.platform.PlatformAccountRequest;
import com.gescom.backend.dto.platform.PlatformSettingsRequest;
import com.gescom.backend.dto.platform.PlatformSettingsResponse;
import com.gescom.backend.entity.PlatformSettings;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.repository.PlatformSettingsRepository;
import com.gescom.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglages de la plateforme et compte du proprietaire.
 *
 * Le parametrage suit le meme motif que {@code SettingsService} : un singleton persistant,
 * cree a la volee au premier acces. La difference tient au perimetre — celui-ci n'appartient
 * a aucune entreprise et n'est donc pas cloisonne.
 */
@Service
@Transactional
public class PlatformSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettingsService.class);

    private final PlatformSettingsRepository platformSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public PlatformSettingsService(PlatformSettingsRepository platformSettingsRepository,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   PasswordPolicy passwordPolicy) {
        this.platformSettingsRepository = platformSettingsRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Reglages courants, crees avec leurs valeurs par defaut s'ils n'existent pas encore.
     *
     * V20_2 seme deja la ligne ; ce repli couvre une base restauree depuis une sauvegarde
     * anterieure, ou la ligne aurait ete supprimee a la main.
     */
    @Transactional(readOnly = true)
    public PlatformSettings getSettings() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(() -> platformSettingsRepository.save(new PlatformSettings()));
    }

    public PlatformSettings updateSettings(PlatformSettingsRequest request) {
        PlatformSettings settings = getSettings();
        settings.setRenewalWindowDays(request.renewalWindowDays());
        settings.setTrialAlertDays(request.trialAlertDays());
        settings.setRevenueHistoryMonths(request.revenueHistoryMonths());
        settings.setOverduePenaltyPoints(request.overduePenaltyPoints());
        settings.setFailedPaymentPenaltyPoints(request.failedPaymentPenaltyPoints());
        return platformSettingsRepository.save(settings);
    }

    /**
     * Met a jour l'email et, si demande, le mot de passe du proprietaire.
     *
     * C'est le seul moyen de faire tourner ce secret depuis l'application :
     * {@code PlatformAdminBootstrap} ne reecrit jamais un compte existant — a dessein, pour
     * qu'une rotation survive aux redeploiements — de sorte que modifier {@code .env} apres
     * la premiere creation n'a aucun effet.
     *
     * Le mot de passe actuel est verifie meme quand seul l'email change : detourner l'adresse
     * de recuperation depuis une session laissee ouverte suffirait sinon a prendre le
     * controle de la plateforme entiere.
     */
    public User updateAccount(User owner, PlatformAccountRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), owner.getPassword())) {
            throw BusinessException.of("platform.account.wrongPassword",
                    "Le mot de passe actuel est incorrect");
        }

        String email = request.email().trim();
        if (!email.equalsIgnoreCase(owner.getEmail())
                && Boolean.TRUE.equals(userRepository.existsByEmail(email))) {
            throw new DuplicateResourceException("user", "email", email);
        }
        owner.setEmail(email);

        String newPassword = request.newPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            passwordPolicy.validate(newPassword);
            if (passwordEncoder.matches(newPassword, owner.getPassword())) {
                throw BusinessException.of("platform.account.samePassword",
                        "Le nouveau mot de passe doit differer de l'actuel");
            }
            owner.setPassword(passwordEncoder.encode(newPassword));
            log.info("Mot de passe du compte proprietaire « {} » modifie.", owner.getUsername());
        }

        return userRepository.save(owner);
    }

    public PlatformSettingsResponse toResponse(PlatformSettings settings, User owner) {
        return new PlatformSettingsResponse(
                settings.getRenewalWindowDays(),
                settings.getTrialAlertDays(),
                settings.getRevenueHistoryMonths(),
                settings.getOverduePenaltyPoints(),
                settings.getFailedPaymentPenaltyPoints(),
                settings.getUpdatedAt(),
                new PlatformSettingsResponse.Account(
                        owner.getId(),
                        owner.getUsername(),
                        owner.getEmail(),
                        owner.getFirstName() + " " + owner.getLastName())
        );
    }
}

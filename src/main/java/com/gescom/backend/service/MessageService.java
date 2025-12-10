package com.gescom.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MessageService {

    @Autowired
    private MessageSource messageSource;

    /**
     * Get internationalized message
     * @param code Message code
     * @return Translated message
     */
    public String getMessage(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get internationalized message with parameters
     * @param code Message code
     * @param args Message arguments
     * @return Translated message with parameters
     */
    public String getMessage(String code, Object[] args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get internationalized message with default value
     * @param code Message code
     * @param defaultMessage Default message if code not found
     * @return Translated message or default
     */
    public String getMessage(String code, String defaultMessage) {
        return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * Get internationalized message for specific locale
     * @param code Message code
     * @param locale Specific locale
     * @return Translated message
     */
    public String getMessage(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    /**
     * Get internationalized message with parameters for specific locale
     * @param code Message code
     * @param args Message arguments
     * @param locale Specific locale
     * @return Translated message with parameters
     */
    public String getMessage(String code, Object[] args, Locale locale) {
        return messageSource.getMessage(code, args, locale);
    }
}

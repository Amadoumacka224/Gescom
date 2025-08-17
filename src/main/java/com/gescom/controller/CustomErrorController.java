package com.gescom.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            
            switch (statusCode) {
                case 400:
                    model.addAttribute("errorTitle", "Requête incorrecte");
                    model.addAttribute("errorMessage", "La requête envoyée au serveur est incorrecte ou malformée.");
                    return "error/400";
                    
                case 401:
                    model.addAttribute("errorTitle", "Non autorisé");
                    model.addAttribute("errorMessage", "Vous devez vous connecter pour accéder à cette ressource.");
                    return "error/401";
                    
                case 403:
                    model.addAttribute("errorTitle", "Accès refusé");
                    model.addAttribute("errorMessage", "Vous n'avez pas les permissions nécessaires pour accéder à cette page.");
                    return "error/403";
                    
                case 404:
                    model.addAttribute("errorTitle", "Page non trouvée");
                    model.addAttribute("errorMessage", "La page que vous recherchez n'existe pas ou a été déplacée.");
                    return "error/404";
                    
                case 500:
                    model.addAttribute("errorTitle", "Erreur interne du serveur");
                    model.addAttribute("errorMessage", "Une erreur inattendue s'est produite sur le serveur.");
                    return "error/500";
                    
                case 503:
                    model.addAttribute("errorTitle", "Service indisponible");
                    model.addAttribute("errorMessage", "Le service est temporairement indisponible. Veuillez réessayer plus tard.");
                    return "error/503";
                    
                default:
                    model.addAttribute("errorTitle", "Erreur " + statusCode);
                    model.addAttribute("errorMessage", "Une erreur inattendue s'est produite.");
                    return "error/error";
            }
        }
        
        model.addAttribute("errorTitle", "Erreur inconnue");
        model.addAttribute("errorMessage", "Une erreur inconnue s'est produite.");
        return "error/error";
    }
}
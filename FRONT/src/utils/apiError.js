/**
 * Extrait un message d'erreur lisible d'une réponse Axios.
 *
 * Le backend peut renvoyer soit une chaîne, soit un objet { message } (erreur métier via
 * `GlobalExceptionHandler`), soit un objet de violations de validation : on évite ainsi
 * d'afficher « [object Object] » à l'utilisateur.
 */
import i18n from '../i18n';

export const extractErrorMessage = (error) => {
  const data = error?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data?.message) return data.message;
  if (data?.errors && typeof data.errors === 'object') {
    const first = Object.values(data.errors)[0];
    if (first) return Array.isArray(first) ? first[0] : String(first);
  }
  return error?.message || i18n.t('common.unexpectedError');
};

export default extractErrorMessage;

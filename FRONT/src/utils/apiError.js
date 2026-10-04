/**
 * Récupère un message d'erreur lisible depuis une réponse Axios.
 *
 * Le backend peut renvoyer une chaîne, un objet { message } (erreur métier via
 * `GlobalExceptionHandler`) ou un objet avec des erreurs de validation : comme ça on
 * n'affiche jamais « [object Object] » à l'utilisateur.
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

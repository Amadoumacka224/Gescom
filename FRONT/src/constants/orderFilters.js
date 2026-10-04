/**
 * Filtres de la page Commandes, avec leurs valeurs vides.
 *
 * Mis ici et pas dans `components/OrderFilters.jsx` : un fichier qui exporte un composant et
 * une constante casse le hot reload de Vite (react-refresh).
 *
 * Utilisé pour l'état initial, pour le bouton « Réinitialiser » et pour savoir quels filtres
 * sont actifs. Pour ajouter un filtre, il faut aussi ajouter son champ dans `OrderFilters`
 * et sa condition dans le filtrage de `Orders`.
 */
export const EMPTY_ORDER_FILTERS = {
  q: '',
  status: 'ALL',
  payment: 'ALL',
  clientId: '',
  clientType: 'ALL',
  city: '',
  productId: '',
  categoryId: '',
  createdById: '',
  dateFrom: '',
  dateTo: '',
  amountMin: '',
  amountMax: '',
  notes: '',
  onlyDiscounted: false,
};

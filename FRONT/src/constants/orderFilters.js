/**
 * Critères de recherche de la page Commandes, à l'état neutre.
 *
 * Déclaré ici plutôt que dans `components/OrderFilters.jsx` : un module qui exporte à la fois
 * un composant et une constante casse le rafraîchissement à chaud de Vite (react-refresh).
 *
 * Sert de trois façons : valeur initiale de l'état, cible du bouton « Réinitialiser », et
 * référence pour savoir quels critères sont actifs. Ajouter un critère ici ne suffit pas —
 * il faut aussi son champ dans `OrderFilters` et sa condition dans le filtrage de `Orders`.
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

import { useCallback, useState } from 'react';

/**
 * Numéro de page d'une liste, qui revient à 1 dès que `resetKey` change (recherche, filtres,
 * tri, nombre de lignes par page).
 *
 * Rester en page 4 alors que la nouvelle liste n'a plus qu'une page afficherait un tableau
 * vide. Les écrans le faisaient avec un effet `setCurrentPage(1)` : la liste était d'abord
 * rendue, et sur les écrans paginés côté serveur demandée, avec l'ancienne page, puis une
 * seconde fois avec la page 1. Ici la page est déduite au rendu : on retient pour quelle clé
 * elle a été choisie, et une autre clé vaut page 1, dès le premier rendu.
 *
 * `resetKey` doit être une valeur comparable avec === : une chaîne ou un nombre, pas un
 * objet recréé à chaque rendu.
 */
export const useResettablePage = (resetKey) => {
  const [state, setState] = useState({ key: resetKey, page: 1 });
  const page = state.key === resetKey ? state.page : 1;

  const setPage = useCallback(
    (next) =>
      setState((prev) => {
        const current = prev.key === resetKey ? prev.page : 1;
        return { key: resetKey, page: typeof next === 'function' ? next(current) : next };
      }),
    [resetKey],
  );

  return [page, setPage];
};

export default useResettablePage;

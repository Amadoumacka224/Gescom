import { useCallback, useEffect, useState } from 'react';

/**
 * Lance `load` au montage, puis chaque fois que la fonction change (donc chaque fois que les
 * dépendances de son useCallback changent : page, filtres, tri…), et indique si ce
 * chargement est en cours.
 *
 * Avant, chaque écran faisait `setLoading(true)` au début de sa fonction de chargement,
 * appelée depuis un effet : un rendu de plus à chaque chargement, que le lint de React
 * signale (react-hooks/set-state-in-effect). Ici l'état « en cours » est déduit : on retient
 * pour quelle demande (version de `load`, numéro de rechargement) le dernier chargement s'est
 * terminé, et on compare avec la demande actuelle.
 *
 * - `loading` : vrai tant que la demande actuelle n'a pas abouti (succès ou échec).
 * - `reload()` : relance le chargement en repassant par l'état « en cours » (bouton
 *   Actualiser). Appeler `load` directement, après un enregistrement par exemple, recharge
 *   sans ce passage : les anciennes données restent affichées jusqu'aux nouvelles, au lieu
 *   d'un tableau qui clignote.
 */
export const useAutoLoad = (load) => {
  const [version, setVersion] = useState(0);
  const [done, setDone] = useState(null);

  useEffect(() => {
    let active = true;
    Promise.resolve(load()).finally(() => {
      // Une réponse arrivée après un changement de page ou de filtre ne doit pas marquer
      // comme chargée la nouvelle demande.
      if (active) setDone({ load, version });
    });
    return () => {
      active = false;
    };
  }, [load, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const loading = done?.load !== load || done?.version !== version;

  return { loading, reload };
};

export default useAutoLoad;

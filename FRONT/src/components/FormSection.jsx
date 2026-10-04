/**
 * Section de formulaire : titre et description à gauche, champs à droite sur grand écran.
 *
 * Ça découpe le formulaire en groupes et laisse de la place pour expliquer chaque groupe.
 *
 * À mettre dans un conteneur `divide-y` pour avoir les séparateurs automatiquement
 * (sans trait en trop au début ou à la fin).
 */
const FormSection = ({ icon: Icon, title, description, children }) => (
  <section className="grid gap-4 py-6 first:pt-0 last:pb-0 lg:grid-cols-[minmax(0,12rem)_minmax(0,1fr)] lg:gap-8">
    <div className="flex items-start gap-3">
      {Icon && (
        <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-600 dark:bg-primary-500/15 dark:text-primary-300">
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
      )}
      <div className="min-w-0">
        <h3 className="subsection-title">{title}</h3>
        {description && (
          <p className="mt-1 text-xs leading-relaxed text-gray-500 dark:text-gray-400">{description}</p>
        )}
      </div>
    </div>
    <div className="space-y-5">{children}</div>
  </section>
);

export default FormSection;

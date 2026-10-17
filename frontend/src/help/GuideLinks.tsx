const LINK_CLASS = 'block text-sm text-primary hover:underline';

/**
 * Links at the foot of a help panel to the guides that cover the same screen: the page of the docs site
 * (`doc` is its address after /docs/, such as "installing#first-run") and the printable guides.
 */
export function GuideLinks({ admin = false, meeting = false, doc }: { admin?: boolean; meeting?: boolean; doc?: string }) {
  return (
    <div className="mt-4 pt-4 border-t space-y-1">
      {doc && (
        <a href={`/docs/${doc}`} target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
          Read more in the docs
        </a>
      )}
      {admin && (
        <a href="/print/admin-guide" target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
          Open Admin Configuration Guide (printable)
        </a>
      )}
      {meeting && (
        <a href="/print/meeting-guide" target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
          Open Race Meeting Guide (printable)
        </a>
      )}
    </div>
  );
}

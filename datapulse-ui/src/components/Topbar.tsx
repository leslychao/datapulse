import { Breadcrumbs } from "./Breadcrumbs";

export const Topbar = () => {
  return (
    <header className="topbar">
      <Breadcrumbs />
      <div className="topbar__actions">
        <button type="button" className="button button--ghost">
          Export
        </button>
      </div>
    </header>
  );
};

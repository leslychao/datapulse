import { NavLink } from "react-router-dom";
import { navSections } from "../routes";

export const Sidebar = () => {
  return (
    <aside className="sidebar">
      <div className="sidebar__brand">Datapulse</div>
      <nav className="sidebar__nav">
        {navSections.map((section) => (
          <div className="sidebar__section" key={section.title}>
            <p className="sidebar__section-title">{section.title}</p>
            <div className="sidebar__items">
              {section.items.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `sidebar__link ${isActive ? "is-active" : ""}`
                  }
                  data-testid={item.testId}
                  end
                >
                  <span className="sidebar__dot" aria-hidden="true" />
                  <span>{item.label}</span>
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
};

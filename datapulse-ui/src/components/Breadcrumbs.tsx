import { Link, useLocation } from "react-router-dom";
import { routeLabels } from "../routes";

export const Breadcrumbs = () => {
  const location = useLocation();
  const path = location.pathname === "/" ? "/" : location.pathname;
  const label = routeLabels[path] ?? "Dashboard";

  return (
    <div className="breadcrumbs">
      <Link to="/" className="breadcrumbs__home">
        Overview
      </Link>
      <span className="breadcrumbs__divider">/</span>
      <span className="breadcrumbs__current">{label}</span>
    </div>
  );
};

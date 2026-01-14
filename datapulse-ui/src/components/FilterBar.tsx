export type FilterField = {
  id: string;
  label: string;
  placeholder: string;
  kind: "select" | "date" | "search" | "text";
};

type FilterBarProps = {
  fields: FilterField[];
};

export const FilterBar = ({ fields }: FilterBarProps) => {
  return (
    <div className="filter-bar" data-testid="filter-bar">
      {fields.map((field) => {
        if (field.kind === "date") {
          return (
            <label className="filter-field" key={field.id}>
              <span className="filter-field__label">{field.label}</span>
              <input
                type="date"
                className="filter-field__input"
                placeholder={field.placeholder}
                data-testid={`filter-${field.id}`}
              />
            </label>
          );
        }

        if (field.kind === "search") {
          return (
            <label className="filter-field" key={field.id}>
              <span className="filter-field__label">{field.label}</span>
              <input
                type="search"
                className="filter-field__input"
                placeholder={field.placeholder}
                data-testid={`filter-${field.id}`}
              />
            </label>
          );
        }

        return (
          <label className="filter-field" key={field.id}>
            <span className="filter-field__label">{field.label}</span>
            <select
              className="filter-field__input"
              data-testid={`filter-${field.id}`}
            >
              <option>{field.placeholder}</option>
            </select>
          </label>
        );
      })}
    </div>
  );
};

import { ReactNode, useState } from "react";

type Tab = {
  id: string;
  label: string;
  content: ReactNode;
  testId?: string;
};

type TabsProps = {
  tabs: Tab[];
  initialId?: string;
};

export const Tabs = ({ tabs, initialId }: TabsProps) => {
  const [activeId, setActiveId] = useState(initialId ?? tabs[0]?.id);
  const activeTab = tabs.find((tab) => tab.id === activeId) ?? tabs[0];

  return (
    <div className="tabs">
      <div className="tabs__list" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`tabs__tab ${tab.id === activeId ? "is-active" : ""}`}
            onClick={() => setActiveId(tab.id)}
            data-testid={tab.testId}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="tabs__panel">{activeTab?.content}</div>
    </div>
  );
};

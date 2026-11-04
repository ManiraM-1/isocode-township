import { NavLink, Outlet } from "react-router-dom";
import { useMe, useSignOut } from "../../lib/session";
import { DemoResetButton } from "./DemoResetButton";

export const DESK_NAV = [
  { to: "/desk/complaints", label: "Complaints" },
  { to: "/desk/payments", label: "Payments" },
  { to: "/desk/committee", label: "Committee" },
] as const;

export function DeskLayout() {
  const me = useMe();
  const signOut = useSignOut();
  return (
    <div className="min-h-screen bg-canvas md:flex">
      <aside className="no-print hidden w-56 shrink-0 border-r border-line bg-surface md:flex md:min-h-screen md:flex-col">
        <div className="px-5 pb-4 pt-6">
          <p className="text-sm font-semibold text-ink">Help desk</p>
          <p className="text-xs text-ink-faint">
            {me.data?.name}
            {me.data?.isDutyManager && " · duty manager"}
          </p>
        </div>
        <nav aria-label="Desk" className="flex-1 px-3">
          <ul className="space-y-1">
            {DESK_NAV.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    `flex min-h-touch items-center rounded-xl px-3 text-sm font-medium ${
                      isActive ? "bg-accent-soft text-accent" : "text-ink-soft hover:bg-canvas hover:text-ink"
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="space-y-1 p-3">
          <DemoResetButton />
          <button
            type="button"
            onClick={signOut}
            className="min-h-touch w-full rounded-xl px-3 text-left text-sm text-ink-soft hover:bg-canvas hover:text-ink"
          >
            Sign out
          </button>
        </div>
      </aside>

      <header className="no-print sticky top-0 z-30 border-b border-line bg-canvas/95 backdrop-blur md:hidden">
        <div className="relative flex h-12 items-center justify-between px-4">
          <span className="text-sm font-semibold text-ink">Help desk</span>
          <div className="flex items-center">
            <DemoResetButton compact />
            <button type="button" onClick={signOut} className="min-h-touch px-2 text-sm text-ink-soft">
              Sign out
            </button>
          </div>
        </div>
        <nav aria-label="Desk sections" className="flex gap-1 overflow-x-auto px-3 pb-2">
          {DESK_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex min-h-touch items-center whitespace-nowrap rounded-xl px-4 text-sm font-medium ${
                  isActive ? "bg-accent-soft text-accent" : "text-ink-soft"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <div className="min-w-0 flex-1">
        <Outlet />
      </div>
    </div>
  );
}

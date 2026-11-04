import { NavLink, Outlet } from "react-router-dom";
import { useMe, useSignOut } from "../../lib/session";
import { EmptyState } from "../../components/States";

export function useResidentFlat() {
  const me = useMe();
  const flat = me.data?.flats[0];
  return { me: me.data!, flatId: flat?.id ?? 0, flatCode: flat?.code ?? "" };
}

const NAV = [
  { to: "/home", label: "Home", icon: "⌂" },
  { to: "/bills", label: "Bills", icon: "₹" },
  { to: "/complaints", label: "Complaints", icon: "✎" },
] as const;

export function ResidentLayout() {
  const { me, flatId, flatCode } = useResidentFlat();
  const signOut = useSignOut();
  return (
    <div className="min-h-screen bg-canvas">
      <header className="no-print sticky top-0 z-30 border-b border-line bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-2xl items-center justify-between px-4">
          <span className="min-w-0 truncate text-sm font-semibold text-ink">
            {flatCode || "Township"}
            <span className="ml-2 font-normal text-ink-faint">{me.name}</span>
          </span>
          <button
            type="button"
            onClick={signOut}
            className="min-h-touch rounded-lg px-2 text-sm text-ink-soft hover:text-ink"
          >
            Sign out
          </button>
        </div>
      </header>
      {flatId ? (
        <Outlet />
      ) : (
        <main className="mx-auto max-w-2xl px-4 pt-10">
          <EmptyState
            title="No flat is linked to your number"
            body="Please ask the help desk to add you to your flat. Once they do, your dues and complaints appear here."
          />
        </main>
      )}
      <nav
        aria-label="Main"
        className="no-print fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur"
      >
        <ul className="mx-auto flex max-w-2xl">
          {NAV.map((item) => (
            <li key={item.to} className="flex-1">
              <NavLink
                to={item.to}
                className={({ isActive }) =>
                  `flex min-h-[56px] flex-col items-center justify-center gap-0.5 text-xs font-medium ${
                    isActive ? "text-accent" : "text-ink-faint hover:text-ink"
                  }`
                }
              >
                <span aria-hidden className="text-lg leading-none">
                  {item.icon}
                </span>
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}

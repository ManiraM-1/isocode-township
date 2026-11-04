import { Outlet } from "react-router-dom";
import { useMe, useSignOut } from "../../lib/session";

export function DeskLayout() {
  const me = useMe();
  const signOut = useSignOut();
  return (
    <div className="min-h-screen bg-canvas">
      <header className="no-print sticky top-0 z-30 border-b border-line bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <span className="text-sm font-semibold text-ink">
            Help desk<span className="ml-2 font-normal text-ink-faint">{me.data?.name}</span>
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
      <Outlet />
    </div>
  );
}

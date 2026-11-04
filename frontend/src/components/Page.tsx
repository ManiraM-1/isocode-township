import { ReactNode } from "react";
import { Link } from "react-router-dom";

type PageProps = {
  title: ReactNode;
  subtitle?: ReactNode;
  back?: { to: string; label: string };
  action?: ReactNode;
  children: ReactNode;
  width?: "narrow" | "wide";
};

export function Page({ title, subtitle, back, action, children, width = "narrow" }: PageProps) {
  return (
    <main className={`mx-auto w-full px-4 pb-28 pt-5 md:pb-12 ${width === "wide" ? "max-w-6xl" : "max-w-2xl"}`}>
      {back && (
        <Link
          to={back.to}
          className="-ml-2 mb-2 inline-flex min-h-touch items-center gap-1 rounded-lg px-2 text-sm text-ink-soft hover:text-ink"
        >
          <span aria-hidden>←</span> {back.label}
        </Link>
      )}
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
    </main>
  );
}

import { ReactNode, useId } from "react";

type FieldProps = {
  label: string;
  error?: string | null;
  hint?: ReactNode;
  children: (props: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode;
};

export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink">
        {label}
      </label>
      {children({ id, describedBy, invalid: !!error })}
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-xs text-ink-faint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

export function inputClass(invalid = false) {
  return (
    "block w-full min-h-touch rounded-xl border bg-surface px-3.5 text-[15px] text-ink placeholder:text-ink-faint " +
    "focus:outline-none focus:ring-2 focus:ring-accent/30 " +
    (invalid ? "border-danger focus:border-danger" : "border-line-strong focus:border-accent")
  );
}

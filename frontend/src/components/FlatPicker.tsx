import { useId, useState } from "react";
import { FlatRef } from "../lib/types";
import { useFlatSearch } from "../features/desk/queries";
import { inputClass } from "./Field";

export function FlatPicker({ onPick, suggestions = [] }: { onPick: (flat: FlatRef) => void; suggestions?: FlatRef[] }) {
  const [query, setQuery] = useState("");
  const id = useId();
  const search = useFlatSearch(query);
  const typed = query.trim().length >= 2;

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink">
        Find the flat
      </label>
      <input
        id={id}
        className={inputClass(false)}
        placeholder="Flat code or resident name, e.g. T12-0408 or Swati"
        value={query}
        autoComplete="off"
        onChange={(event) => setQuery(event.target.value)}
      />
      {!typed && suggestions.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {suggestions.map((flat) => (
            <button
              key={flat.id}
              type="button"
              onClick={() => onPick(flat)}
              className="min-h-touch rounded-full border border-line-strong px-3.5 text-sm font-medium text-ink hover:border-accent"
            >
              {flat.code}
            </button>
          ))}
        </div>
      )}
      {typed && (
        <div className="mt-2 max-h-60 overflow-y-auto rounded-xl border border-line">
          {search.isPending ? (
            <p className="px-3 py-3 text-sm text-ink-soft">Searching…</p>
          ) : search.isError ? (
            <p className="px-3 py-3 text-sm text-danger">{search.error.message}</p>
          ) : search.data.length === 0 ? (
            <p className="px-3 py-3 text-sm text-ink-soft">No flat matches “{query.trim()}”.</p>
          ) : (
            <ul className="divide-y divide-line">
              {search.data.map((flat) => (
                <li key={flat.id}>
                  <button
                    type="button"
                    onClick={() => onPick({ id: flat.id, code: flat.code })}
                    className="flex min-h-touch w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-canvas"
                  >
                    <span className="font-medium tabular-nums text-ink">{flat.code}</span>
                    <span className="truncate text-sm text-ink-soft">{flat.residents.join(", ")}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

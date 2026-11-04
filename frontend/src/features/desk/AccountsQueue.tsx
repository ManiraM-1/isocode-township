import { FormEvent, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../lib/api";
import { formatDate } from "../../lib/format";
import { BankRow, FlatRef, QueueActionResponse } from "../../lib/types";
import { DecisionBadge } from "../../components/Badge";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { FlatPicker } from "../../components/FlatPicker";
import { Money } from "../../components/Money";
import { EmptyState, ErrorState, InlineError, SkeletonList } from "../../components/States";
import { useToast } from "../../components/Toast";
import { deskKeys, useAccountsQueue } from "./queries";

export function AccountsQueue() {
  const queue = useAccountsQueue();
  if (queue.isPending) return <SkeletonList rows={3} />;
  if (queue.isError) return <ErrorState error={queue.error} onRetry={() => queue.refetch()} />;
  if (queue.data.length === 0) {
    return (
      <EmptyState
        title="The queue is clear"
        body="Every imported payment has been posted or decided. New unclear payments appear here after the next import."
      />
    );
  }
  return (
    <ul className="space-y-3">
      {queue.data.map((row) => (
        <li key={row.id}>
          <QueueRow row={row} />
        </li>
      ))}
    </ul>
  );
}

type Mode = "idle" | "assign" | "ignore";

export function QueueRow({ row }: { row: BankRow }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [mode, setMode] = useState<Mode>("idle");
  const [assignTo, setAssignTo] = useState<FlatRef | null>(null);
  const [note, setNote] = useState("");
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  const act = useMutation({
    mutationFn: ({ path, body }: { path: string; body: unknown }) =>
      api.post<QueueActionResponse>(`/api/desk/bank-rows/${row.id}/${path}`, body),
    onSuccess: (response) => {
      const resolved = response.row;
      toast(
        resolved.resolution === "IGNORED"
          ? "Marked as not a maintenance payment"
          : `Posted to ${resolved.suggestedFlat?.code ?? assignTo?.code ?? "the flat"}${
              resolved.receiptNo ? ` · receipt ${resolved.receiptNo}` : ""
            }`,
      );
      response.warnings.forEach((warning) => toast(warning, "info"));
      queryClient.invalidateQueries({ queryKey: deskKeys.queue });
      queryClient.invalidateQueries({ queryKey: deskKeys.imports });
      queryClient.invalidateQueries({ queryKey: ["desk", "imports", row.importId] });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        queryClient.invalidateQueries({ queryKey: deskKeys.queue });
      }
    },
  });

  const locked = act.isPending || act.isSuccess;
  const suggested = row.decision === "SUGGESTED" && row.suggestedFlat;
  const reasonError = reason.trim().length < 3 ? "Say why this is not a maintenance payment (at least 3 characters)" : null;
  const candidates = [...(row.suggestedFlat ? [row.suggestedFlat] : []), ...row.candidates].filter(
    (flat, index, all) => all.findIndex((other) => other.id === flat.id) === index,
  );

  function submitAssign(event: FormEvent) {
    event.preventDefault();
    if (assignTo) act.mutate({ path: "assign", body: { flatId: assignTo.id, note: note.trim() || null } });
  }

  function submitIgnore(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!reasonError) act.mutate({ path: "ignore", body: { reason: reason.trim() } });
  }

  return (
    <Card className={`p-4 ${locked ? "opacity-60" : ""}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <DecisionBadge decision={row.decision} />
            <span className="text-xs tabular-nums text-ink-faint">
              {formatDate(row.txnDate)} · row {row.rowNo}
            </span>
          </div>
          <p className="mt-1 break-words text-sm text-ink">{row.description}</p>
          <p className="mt-0.5 break-all text-xs text-ink-faint">
            {[row.reference, row.utr && `UTR ${row.utr}`, row.upiId].filter(Boolean).join(" · ")}
          </p>
        </div>
        <Money amount={row.amount} className="text-lg font-semibold" />
      </div>
      {row.reasons.length > 0 && (
        <ul className="mt-3 space-y-1 rounded-xl bg-canvas px-3 py-2 text-sm text-ink-soft">
          {row.reasons.map((reasonText, index) => (
            <li key={index}>{reasonText}</li>
          ))}
        </ul>
      )}
      {candidates.length > 0 && (
        <p className="mt-2 text-sm text-ink-soft">
          Could be: <span className="font-medium tabular-nums text-ink">{candidates.map((flat) => flat.code).join(", ")}</span>
        </p>
      )}

      {mode === "idle" && (
        <div className="mt-3 flex flex-wrap gap-2">
          {suggested && (
            <Button
              size="sm"
              busy={act.isPending}
              disabled={locked}
              onClick={() => act.mutate({ path: "confirm", body: {} })}
            >
              Confirm {row.suggestedFlat!.code}
            </Button>
          )}
          <Button size="sm" variant="secondary" disabled={locked} onClick={() => setMode("assign")}>
            Assign to flat
          </Button>
          <Button size="sm" variant="ghost" disabled={locked} onClick={() => setMode("ignore")}>
            Not maintenance
          </Button>
        </div>
      )}

      {mode === "assign" && (
        <form onSubmit={submitAssign} className="mt-3 space-y-3 border-t border-line pt-3">
          {assignTo ? (
            <p className="text-sm text-ink">
              Post <Money amount={row.amount} align="left" /> to <span className="font-semibold">{assignTo.code}</span>{" "}
              <button type="button" className="min-h-touch px-1 font-medium text-accent" onClick={() => setAssignTo(null)}>
                Change
              </button>
            </p>
          ) : (
            <FlatPicker onPick={setAssignTo} suggestions={candidates} />
          )}
          {assignTo && (
            <input
              aria-label="Note (optional)"
              className="block min-h-touch w-full rounded-xl border border-line-strong bg-surface px-3.5 text-sm focus:border-accent focus:outline-none"
              placeholder="Note (optional), e.g. confirmed by phone with the owner"
              maxLength={500}
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          )}
          <div className="flex gap-2">
            <Button size="sm" type="submit" disabled={!assignTo || locked} busy={act.isPending}>
              Post to flat
            </Button>
            <Button size="sm" variant="ghost" disabled={act.isPending} onClick={() => setMode("idle")}>
              Cancel
            </Button>
          </div>
        </form>
      )}

      {mode === "ignore" && (
        <form onSubmit={submitIgnore} noValidate className="mt-3 space-y-3 border-t border-line pt-3">
          <div>
            <label htmlFor={`reason-${row.id}`} className="mb-1.5 block text-sm font-medium text-ink">
              Why is this not a maintenance payment?
            </label>
            <input
              id={`reason-${row.id}`}
              className="block min-h-touch w-full rounded-xl border border-line-strong bg-surface px-3.5 text-sm focus:border-accent focus:outline-none"
              placeholder="e.g. Clubhouse booking deposit"
              maxLength={500}
              value={reason}
              aria-invalid={touched && !!reasonError}
              onChange={(event) => setReason(event.target.value)}
            />
            {touched && reasonError && <p className="mt-1.5 text-sm text-danger">{reasonError}</p>}
          </div>
          <div className="flex gap-2">
            <Button size="sm" type="submit" variant="danger" disabled={locked} busy={act.isPending}>
              Mark not maintenance
            </Button>
            <Button size="sm" variant="ghost" disabled={act.isPending} onClick={() => setMode("idle")}>
              Cancel
            </Button>
          </div>
        </form>
      )}
      <div className="mt-2">
        <InlineError error={act.error} />
      </div>
    </Card>
  );
}

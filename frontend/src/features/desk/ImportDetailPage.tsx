import { useState } from "react";
import { useParams } from "react-router-dom";
import { decisionLabel, DecisionBadge } from "../../components/Badge";
import { Card } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { ErrorState, Skeleton } from "../../components/States";
import { formatDate, formatDateTime } from "../../lib/format";
import { BankImportSummary, BankRow, Decision } from "../../lib/types";
import { QueueRow } from "./AccountsQueue";
import { useImportDetails } from "./queries";

const COUNTS: { key: keyof BankImportSummary; decision: Decision; tone: string }[] = [
  { key: "autoPosted", decision: "AUTO_POSTED", tone: "text-accent" },
  { key: "reconciled", decision: "RECONCILED_GATEWAY", tone: "text-accent" },
  { key: "suggested", decision: "SUGGESTED", tone: "text-caution" },
  { key: "conflicts", decision: "CONFLICT", tone: "text-danger" },
  { key: "unmatched", decision: "UNMATCHED", tone: "text-caution" },
  { key: "possibleDuplicates", decision: "POSSIBLE_DUPLICATE", tone: "text-caution" },
  { key: "duplicates", decision: "DUPLICATE", tone: "text-ink-soft" },
  { key: "nonCredit", decision: "NON_CREDIT", tone: "text-ink-soft" },
  { key: "errors", decision: "PARSE_ERROR", tone: "text-danger" },
];

export function ImportDetailPage() {
  const id = Number(useParams().importId);
  const details = useImportDetails(id);
  const [filter, setFilter] = useState<Decision | "">("");

  return (
    <Page
      width="wide"
      title={details.data?.summary.fileName ?? "Import"}
      subtitle={
        details.data &&
        `${details.data.summary.totalRows} rows · ${formatDateTime(details.data.summary.uploadedAt)} · ${details.data.summary.uploadedBy}`
      }
      back={{ to: "/desk/payments", label: "Payments" }}
    >
      {details.isPending ? (
        <div className="space-y-4">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-72 w-full" />
        </div>
      ) : details.isError ? (
        <ErrorState error={details.error} onRetry={() => details.refetch()} />
      ) : (
        <>
          {details.data.summary.sameFileImportedBefore && (
            <p className="mb-4 rounded-xl bg-caution-soft px-4 py-3 text-sm text-caution">
              This exact file was imported before, on {formatDateTime(details.data.summary.sameFileImportedBefore)}. Nothing
              was posted twice.
            </p>
          )}
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-9">
            {COUNTS.map((count) => {
              const value = details.data.summary[count.key] as number;
              const active = filter === count.decision;
              return (
                <button
                  key={count.key}
                  type="button"
                  aria-pressed={active}
                  disabled={value === 0}
                  onClick={() => setFilter(active ? "" : count.decision)}
                  className={`rounded-card border bg-surface px-3 py-2.5 text-left transition-colors disabled:opacity-50 ${
                    active ? "border-accent ring-2 ring-accent/20" : "border-line hover:border-line-strong"
                  }`}
                >
                  <span className={`block text-xl font-semibold tabular-nums ${count.tone}`}>{value}</span>
                  <span className="block text-xs text-ink-soft">{decisionLabel(count.decision)}</span>
                </button>
              );
            })}
          </div>
          <RowsTable rows={details.data.rows.filter((row) => !filter || row.decision === filter)} />
        </>
      )}
    </Page>
  );
}

function RowsTable({ rows }: { rows: BankRow[] }) {
  const queued = rows.filter((row) => row.openInQueue);
  const others = rows.filter((row) => !row.openInQueue);
  return (
    <div className="mt-6 space-y-6">
      {queued.length > 0 && (
        <section>
          <h2 className="mb-3 text-[15px] font-semibold text-ink">Waiting for you ({queued.length})</h2>
          <ul className="space-y-3">
            {queued.map((row) => (
              <li key={row.id}>
                <QueueRow row={row} />
              </li>
            ))}
          </ul>
        </section>
      )}
      {others.length > 0 && (
        <Card className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-sm">
            <caption className="sr-only">Statement rows and what happened to each</caption>
            <thead className="bg-canvas text-xs text-ink-faint">
              <tr>
                <th scope="col" className="px-4 py-2 text-left font-medium">Row</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">Date</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">Description</th>
                <th scope="col" className="px-2 py-2 text-right font-medium">Amount</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">Result</th>
                <th scope="col" className="px-4 py-2 text-left font-medium">Why</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {others.map((row) => (
                <tr key={row.id} className="align-top">
                  <td className="px-4 py-3 tabular-nums text-ink-faint">{row.rowNo}</td>
                  <td className="whitespace-nowrap px-2 py-3 tabular-nums">{formatDate(row.txnDate)}</td>
                  <td className="max-w-xs px-2 py-3">
                    <p className="break-words text-ink">{row.description ?? "—"}</p>
                    {row.reference && <p className="break-all text-xs text-ink-faint">{row.reference}</p>}
                  </td>
                  <td className="whitespace-nowrap px-2 py-3 text-right">
                    <Money amount={row.amount ?? row.debit} />
                  </td>
                  <td className="px-2 py-3">
                    <DecisionBadge decision={row.decision} />
                    {(row.suggestedFlat || row.receiptNo) && (
                      <p className="mt-1 text-xs tabular-nums text-ink-soft">
                        {row.suggestedFlat?.code}
                        {row.receiptNo && ` · ${row.receiptNo}`}
                      </p>
                    )}
                    {row.resolution && (
                      <p className="mt-1 text-xs text-ink-faint">
                        {row.resolution.toLowerCase()} by {row.resolvedBy}
                      </p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-ink-soft">
                    {row.parseError ?? row.resolutionNote ?? row.reasons.join(" ")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}

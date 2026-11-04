import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { api, downloadFile } from "../../lib/api";
import { CATEGORY_LABEL } from "../../lib/complaints";
import { formatDate, formatDateTime, formatMinutes, formatMonth, formatRupees } from "../../lib/format";
import { CommitteeOverview } from "../../lib/types";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { ErrorState, Skeleton } from "../../components/States";
import { useToast } from "../../components/Toast";

export function currentMonth(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit" })
    .formatToParts(now);
  const year = parts.find((part) => part.type === "year")!.value;
  const month = parts.find((part) => part.type === "month")!.value;
  return `${year}-${month}`;
}

export function meetsTarget(value: string | null, target: string, direction: "atLeast" | "below"): boolean | null {
  if (value === null) return null;
  return direction === "atLeast" ? Number(value) >= Number(target) : Number(value) < Number(target);
}

export function useCommitteeOverview(month: string) {
  return useQuery({
    queryKey: ["committee", "overview", month],
    queryFn: () => api.get<CommitteeOverview>(`/api/committee/overview?month=${month}`),
  });
}

export function CommitteePage() {
  const [params, setParams] = useSearchParams();
  const latest = currentMonth();
  const month = params.get("month") ?? latest;
  const overview = useCommitteeOverview(month);
  const toast = useToast();
  const [downloading, setDownloading] = useState(false);

  async function downloadCsv() {
    setDownloading(true);
    try {
      await downloadFile(`/api/committee/report.csv?month=${month}`, `committee-report-${month}.csv`);
    } catch (error) {
      toast(error instanceof Error ? error.message : "Could not download the report", "error");
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Page
      width="wide"
      title="Committee report"
      subtitle={overview.data ? `${formatMonth(month)} · as at ${formatDate(overview.data.asOf)}` : formatMonth(month)}
      action={
        <div className="no-print flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor="report-month">
            Month
          </label>
          <input
            id="report-month"
            type="month"
            max={latest}
            value={month}
            onChange={(event) => {
              if (event.target.value) setParams({ month: event.target.value }, { replace: true });
            }}
            className="min-h-touch rounded-xl border border-line-strong bg-surface px-3 text-sm tabular-nums focus:border-accent focus:outline-none"
          />
          <Button variant="secondary" size="sm" busy={downloading} onClick={downloadCsv} disabled={!overview.data}>
            Download CSV
          </Button>
          <Button variant="ghost" size="sm" onClick={() => window.print()} disabled={!overview.data}>
            Print
          </Button>
        </div>
      }
    >
      {overview.isPending ? (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-28" />
            ))}
          </div>
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-56 w-full" />
        </div>
      ) : overview.isError ? (
        <ErrorState error={overview.error} onRetry={() => overview.refetch()} />
      ) : (
        <Report data={overview.data} month={month} />
      )}
    </Page>
  );
}

function Report({ data, month }: { data: CommitteeOverview; month: string }) {
  const { collections, reconciliation, complaints } = data;
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        <Kpi
          label="Collection rate"
          value={percent(collections.collectionRate)}
          target={`Target ${collections.onTimeTarget}%`}
          ok={meetsTarget(collections.collectionRate, collections.onTimeTarget, "atLeast")}
          note={`${formatRupees(collections.collected)} of ${formatRupees(collections.totalDue)} due`}
        />
        <Kpi
          label="Paid on time"
          value={percent(collections.onTimeRate)}
          target={`Target ${collections.onTimeTarget}%`}
          ok={meetsTarget(collections.onTimeRate, collections.onTimeTarget, "atLeast")}
          note={`${collections.billsPaidOnTime} of ${collections.billsDue} bills`}
        />
        <Kpi label="Outstanding" value={formatRupees(reconciliation.closingOutstanding)} note="All flats, net of advances" />
        <Kpi
          label="Reopen rate"
          value={percent(complaints.reopenRate)}
          target={`Target under ${complaints.reopenTarget}%`}
          ok={meetsTarget(complaints.reopenRate, complaints.reopenTarget, "below")}
          note={`${complaints.reopened} of ${complaints.opened} complaints`}
        />
        <Kpi
          label="Median first response"
          value={formatMinutes(complaints.medianFirstResponseMinutes)}
          note={
            complaints.firstResponseBreachShare === null
              ? "No responses due yet"
              : `${complaints.firstResponseBreachShare}% missed the target`
          }
        />
      </div>

      <ReconciliationCard data={data} />
      <AgeingCard data={data} month={month} />

      <div className="grid gap-6 lg:grid-cols-2">
        <ComplaintsCard data={data} />
        <MatchingCard data={data} />
      </div>
      <p className="text-xs text-ink-faint">
        Generated {formatDateTime(data.generatedAt)}. Queue, open complaints and escalations are live figures. A payment
        dated in this month but imported later, or next month's late-interest run, can still change these numbers.
      </p>
    </div>
  );
}

function percent(value: string | null): string {
  return value === null ? "—" : `${value}%`;
}

function Kpi({ label, value, target, ok, note }: { label: string; value: string; target?: string; ok?: boolean | null; note?: string }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-medium text-ink-soft">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums tracking-tight text-ink">{value}</p>
      {target && (
        <p className={`mt-1 text-xs font-medium ${ok === false ? "text-danger" : ok ? "text-accent" : "text-ink-faint"}`}>
          {ok === true ? "✓ " : ok === false ? "✗ " : ""}
          {target}
        </p>
      )}
      {note && <p className="mt-0.5 text-xs text-ink-faint">{note}</p>}
    </Card>
  );
}

function ReconciliationCard({ data }: { data: CommitteeOverview }) {
  const r = data.reconciliation;
  const lines: [string, string, string][] = [
    ["Opening outstanding", "", r.openingOutstanding],
    ["Billed", "+", r.billed],
    ["Late fees", "+", r.lateFeesCharged],
    ["Late-fee reversals", "−", r.lateFeeReversals],
    ["Collections", "−", r.collections],
  ];
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-[15px] font-semibold text-ink">Reconciliation</h2>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
            r.balanced ? "bg-accent-soft text-accent" : "bg-danger-soft text-danger"
          }`}
        >
          {r.balanced ? "✓ Balanced with the ledger" : "✗ Does not balance"}
        </span>
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[560px] text-sm">
          <caption className="sr-only">Opening outstanding plus billed and late fees, less reversals and collections</caption>
          <thead>
            <tr className="text-xs text-ink-faint">
              {lines.map(([label]) => (
                <th key={label} scope="col" className="px-2 pb-1 text-right font-medium">
                  {label}
                </th>
              ))}
              <th scope="col" className="px-2 pb-1 text-right font-medium">Closing outstanding</th>
            </tr>
          </thead>
          <tbody>
            <tr className="text-[15px]">
              {lines.map(([label, sign, amount]) => (
                <td key={label} className="whitespace-nowrap px-2 text-right tabular-nums">
                  {sign && <span className="mr-1 text-ink-faint">{sign}</span>}
                  <Money amount={amount} />
                </td>
              ))}
              <td className="whitespace-nowrap px-2 text-right font-semibold tabular-nums">
                <span className="mr-1 text-ink-faint">=</span>
                <Money amount={r.closingOutstanding} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      {!r.balanced && (
        <p className="mt-3 text-sm text-danger">
          The lines add up to {formatRupees(r.expectedClosingOutstanding)}, but the ledger says{" "}
          {formatRupees(r.closingOutstanding)}. Do not publish this report; ask the accounts team to check.
        </p>
      )}
    </Card>
  );
}

const AGEING_FILL: Record<string, string> = {
  "not-yet-due": "#D3CCBE",
  "1-30": "#E8A3A0",
  "31-60": "#D2645F",
  "61-90": "#B4232C",
  "90-plus": "#7A141B",
};

function AgeingCard({ data, month }: { data: CommitteeOverview; month: string }) {
  const [hovered, setHovered] = useState<string | null>(null);
  const owing = data.ageing.buckets.filter((bucket) => bucket.bucket !== "in-credit" && Number(bucket.amount) > 0);
  const owingTotal = owing.reduce((sum, bucket) => sum + Number(bucket.amount), 0);
  const hoveredBucket = data.ageing.buckets.find((bucket) => bucket.bucket === hovered);

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-[15px] font-semibold text-ink">Dues ageing</h2>
        <span className="text-xs text-ink-faint">
          By oldest unpaid due date, as at {formatDate(data.ageing.asOf)}
          {data.ageing.matchesClosingOutstanding ? " · adds up to outstanding" : " · does not match outstanding"}
        </span>
      </div>
      {owing.length > 0 && (
        <div className="relative mt-4">
          <div className="flex h-4 w-full gap-[2px]" role="img" aria-label="Share of dues by age; the table below lists every figure">
            {owing.map((bucket) => (
              <Link
                key={bucket.bucket}
                to={`/desk/committee/ageing/${bucket.bucket}?month=${month}`}
                onMouseEnter={() => setHovered(bucket.bucket)}
                onMouseLeave={() => setHovered(null)}
                onFocus={() => setHovered(bucket.bucket)}
                onBlur={() => setHovered(null)}
                aria-label={`${bucket.label}: ${formatRupees(bucket.amount)}, ${bucket.flats} flats`}
                className="h-full first:rounded-l last:rounded-r"
                style={{
                  width: `${(Number(bucket.amount) / owingTotal) * 100}%`,
                  minWidth: 6,
                  background: AGEING_FILL[bucket.bucket],
                  opacity: hovered && hovered !== bucket.bucket ? 0.5 : 1,
                }}
              />
            ))}
          </div>
          {hoveredBucket && (
            <p className="pointer-events-none absolute -top-9 left-0 rounded-lg bg-ink px-2.5 py-1 text-xs text-white shadow">
              {hoveredBucket.label}: {formatRupees(hoveredBucket.amount)} · {hoveredBucket.flats} flats
            </p>
          )}
        </div>
      )}
      <table className="mt-4 w-full text-sm">
        <caption className="sr-only">Dues ageing by bucket</caption>
        <thead className="text-xs text-ink-faint">
          <tr>
            <th scope="col" className="py-1 text-left font-medium">Age</th>
            <th scope="col" className="py-1 text-right font-medium">Flats</th>
            <th scope="col" className="py-1 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {data.ageing.buckets.map((bucket) => (
            <tr key={bucket.bucket}>
              <td className="py-1.5">
                {bucket.flats > 0 ? (
                  <Link
                    to={`/desk/committee/ageing/${bucket.bucket}?month=${month}`}
                    className="inline-flex min-h-touch items-center gap-2 font-medium text-ink hover:text-accent"
                  >
                    <Swatch bucket={bucket.bucket} />
                    {bucket.label}
                    <span aria-hidden className="text-ink-faint">→</span>
                  </Link>
                ) : (
                  <span className="inline-flex min-h-touch items-center gap-2 text-ink-soft">
                    <Swatch bucket={bucket.bucket} />
                    {bucket.label}
                  </span>
                )}
              </td>
              <td className="py-1.5 text-right tabular-nums">{bucket.flats}</td>
              <td className="py-1.5 text-right">
                <Money amount={bucket.amount} />
              </td>
            </tr>
          ))}
          <tr className="font-semibold">
            <td className="py-2">Total</td>
            <td />
            <td className="py-2 text-right">
              <Money amount={data.ageing.total} />
            </td>
          </tr>
        </tbody>
      </table>
    </Card>
  );
}

function Swatch({ bucket }: { bucket: string }) {
  const fill = AGEING_FILL[bucket];
  return (
    <span
      aria-hidden
      className={`inline-block h-2.5 w-2.5 rounded-sm ${fill ? "" : "border border-accent"}`}
      style={fill ? { background: fill } : undefined}
    />
  );
}

function ComplaintsCard({ data }: { data: CommitteeOverview }) {
  const c = data.complaints;
  const maxOpen = Math.max(1, ...c.openByCategory.map((item) => item.open));
  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Complaints</h2>
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-sm sm:grid-cols-3">
        <Stat label="Opened" value={String(c.opened)} />
        <Stat label="Resolved" value={String(c.resolved)} />
        <Stat label="Median resolution" value={formatMinutes(c.medianResolutionMinutes)} />
        <Stat
          label="Missed first response"
          value={c.firstResponseBreachShare === null ? "—" : `${c.firstResponseBreached} of ${c.firstResponseMeasured}`}
        />
        <Stat label="Average rating" value={c.averageRating ? `${c.averageRating} / 5` : "—"} hint={`${c.ratings} ratings`} />
        <Stat
          label="Safety escalations"
          value={String(c.activeSafetyEscalations)}
          hint={c.unacknowledgedEscalations > 0 ? `${c.unacknowledgedEscalations} not acknowledged` : "all acknowledged"}
          danger={c.unacknowledgedEscalations > 0}
        />
      </dl>
      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wider text-ink-faint">Open now, by category</h3>
      {c.openByCategory.length === 0 ? (
        <p className="mt-2 text-sm text-ink-soft">No open complaints.</p>
      ) : (
        <ul className="mt-2 space-y-1.5">
          {c.openByCategory.map((item) => (
            <li key={item.category} className="grid grid-cols-[110px_1fr_32px] items-center gap-3 text-sm">
              <span className="text-ink">{CATEGORY_LABEL[item.category]}</span>
              <span className="h-2.5 rounded-r bg-accent/70" style={{ width: `${(item.open / maxOpen) * 100}%` }} />
              <span className="text-right tabular-nums text-ink-soft">{item.open}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function MatchingCard({ data }: { data: CommitteeOverview }) {
  const m = data.matching;
  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Payment matching</h2>
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
        <Stat
          label="Matched automatically"
          value={percent(m.autoPostShare)}
          hint={`${m.autoPosted} of ${m.newPaymentRows} payments, ${m.importsThisMonth} imports`}
        />
        <Stat
          label="Waiting in the queue"
          value={String(m.queueSize)}
          hint={m.oldestQueuedDays === null ? "nothing waiting" : `oldest ${m.oldestQueuedDays} days`}
          danger={(m.oldestQueuedDays ?? 0) > 7}
        />
      </dl>
      {m.lastImport ? (
        <div className="mt-4 rounded-xl bg-canvas px-3 py-2.5 text-sm">
          <p className="font-medium text-ink">{m.lastImport.fileName}</p>
          <p className="text-xs text-ink-faint">Last import {formatDateTime(m.lastImport.uploadedAt)}</p>
          <p className="mt-1 text-xs tabular-nums text-ink-soft">
            {m.lastImport.totalRows} rows · {m.lastImport.autoPosted} posted · {m.lastImport.reconciled} reconciled ·{" "}
            {m.lastImport.suggested + m.lastImport.conflicts + m.lastImport.unmatched + m.lastImport.possibleDuplicates} queued ·{" "}
            {m.lastImport.duplicates} duplicates
          </p>
        </div>
      ) : (
        <p className="mt-4 text-sm text-ink-soft">No bank statement imported by the end of this month.</p>
      )}
    </Card>
  );
}

function Stat({ label, value, hint, danger = false }: { label: string; value: string; hint?: string; danger?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className="text-lg font-semibold tabular-nums text-ink">{value}</dd>
      {hint && <dd className={`text-xs ${danger ? "text-danger" : "text-ink-faint"}`}>{hint}</dd>}
    </div>
  );
}

import { useState } from "react";
import { Link } from "react-router-dom";
import { BillStatusBadge } from "../../components/Badge";
import { ButtonLink } from "../../components/Button";
import { Card } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { EmptyState, ErrorState, Skeleton, SkeletonList } from "../../components/States";
import { formatDate, formatMonth, isPositive } from "../../lib/format";
import { useBills, useFlatSummary, useLedger } from "./queries";
import { useResidentFlat } from "./ResidentLayout";

type Tab = "bills" | "statement";

export function BillsPage() {
  const { flatId, flatCode } = useResidentFlat();
  const [tab, setTab] = useState<Tab>("bills");
  return (
    <Page title="Bills" subtitle={`Flat ${flatCode}`}>
      <OutstandingCard flatId={flatId} />
      <div role="tablist" aria-label="Bills view" className="mt-6 grid grid-cols-2 rounded-xl bg-line/50 p-1">
        {(
          [
            ["bills", "Bills"],
            ["statement", "Statement"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            role="tab"
            type="button"
            aria-selected={tab === value}
            onClick={() => setTab(value)}
            className={`min-h-touch rounded-lg text-sm font-medium ${
              tab === value ? "bg-surface text-ink shadow-card" : "text-ink-soft"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="mt-4">{tab === "bills" ? <BillList flatId={flatId} /> : <Statement flatId={flatId} />}</div>
    </Page>
  );
}

function OutstandingCard({ flatId }: { flatId: number }) {
  const summary = useFlatSummary(flatId);
  if (summary.isPending) return <Skeleton className="h-28 w-full" />;
  if (summary.isError) return <ErrorState error={summary.error} onRetry={() => summary.refetch()} />;
  const data = summary.data;
  const owes = isPositive(data.outstanding);
  return (
    <Card className="flex items-center justify-between gap-4 p-5">
      <div>
        <p className="text-sm text-ink-soft">{owes ? "Outstanding" : isPositive(data.advance) ? "Advance" : "Outstanding"}</p>
        <p className="mt-0.5 text-2xl font-semibold tracking-tight">
          <Money amount={owes || !isPositive(data.advance) ? data.outstanding : data.advance} />
        </p>
        {data.daysOverdue > 0 && (
          <p className="mt-0.5 text-sm text-danger">
            <Money amount={data.overdue} /> overdue by {data.daysOverdue} days
          </p>
        )}
      </div>
      {owes && <ButtonLink to="/bills/pay">Pay</ButtonLink>}
    </Card>
  );
}

function BillList({ flatId }: { flatId: number }) {
  const bills = useBills(flatId);
  if (bills.isPending) return <SkeletonList rows={3} />;
  if (bills.isError) return <ErrorState error={bills.error} onRetry={() => bills.refetch()} />;
  if (bills.data.length === 0) {
    return <EmptyState title="No bills yet" body="Your first maintenance bill appears here on the 1st of the month." />;
  }
  return (
    <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
      {bills.data.map((bill) => (
        <li key={bill.id} className="flex items-center justify-between gap-4 px-4 py-3.5">
          <div className="min-w-0">
            <p className="font-medium text-ink">{formatMonth(bill.month)}</p>
            <p className="text-xs text-ink-soft">
              Due {formatDate(bill.dueDate)} · {bill.areaSqft} sq ft × <Money amount={bill.rate} align="left" />
            </p>
          </div>
          <div className="flex flex-col items-end gap-1">
            <Money amount={bill.amount} className="font-medium" />
            {bill.status !== "PAID" && isPositive(bill.amountPaid) ? (
              <span className="text-xs text-ink-soft">
                <Money amount={bill.balance} /> left
              </span>
            ) : (
              <BillStatusBadge status={bill.status} />
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

function Statement({ flatId }: { flatId: number }) {
  const ledger = useLedger(flatId);
  if (ledger.isPending) return <SkeletonList rows={4} />;
  if (ledger.isError) return <ErrorState error={ledger.error} onRetry={() => ledger.refetch()} />;
  if (ledger.data.length === 0) {
    return <EmptyState title="Nothing on your statement yet" body="Bills, late fees and payments are listed here." />;
  }
  const newestFirst = [...ledger.data].reverse();
  return (
    <div className="overflow-hidden rounded-card border border-line bg-surface">
      <table className="w-full text-sm">
        <caption className="sr-only">Statement with running balance, newest first</caption>
        <thead className="bg-canvas text-xs text-ink-faint">
          <tr>
            <th scope="col" className="px-4 py-2 text-left font-medium">Entry</th>
            <th scope="col" className="px-2 py-2 text-right font-medium">Amount</th>
            <th scope="col" className="px-4 py-2 text-right font-medium">Balance</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {newestFirst.map((line) => {
            const credit = isPositive(line.credit);
            return (
              <tr key={line.id}>
                <td className="px-4 py-3 align-top">
                  <p className="text-ink">{line.description}</p>
                  <p className="text-xs text-ink-faint">
                    {formatDate(line.date)}
                    {line.receiptNo && (
                      <>
                        {" · "}
                        <Link to={`/receipts/${line.receiptNo}`} className="font-medium text-accent hover:underline">
                          Receipt {line.receiptNo}
                        </Link>
                      </>
                    )}
                  </p>
                </td>
                <td className={`px-2 py-3 text-right align-top tabular-nums ${credit ? "text-accent" : "text-ink"}`}>
                  {credit ? "−" : "+"}
                  <Money amount={credit ? line.credit : line.debit} />
                </td>
                <td className="px-4 py-3 text-right align-top">
                  <Money amount={line.runningBalance} className="text-ink-soft" />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

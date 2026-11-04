import { Link } from "react-router-dom";
import { Badge, StatusBadge } from "../../components/Badge";
import { ButtonLink } from "../../components/Button";
import { Card, SectionTitle } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { EmptyState, ErrorState, Skeleton } from "../../components/States";
import { formatDate, isPositive, timeSince } from "../../lib/format";
import { Bill, ComplaintSummary, FlatSummary } from "../../lib/types";
import { useBills, useFlatComplaints, useFlatSummary } from "./queries";
import { useResidentFlat } from "./ResidentLayout";

export function ResidentHome() {
  const { me, flatId, flatCode } = useResidentFlat();
  return (
    <Page title={`Hello, ${me.name.split(" ")[0]}`} subtitle={`Flat ${flatCode}`}>
      <DuesCard flatId={flatId} />
      <OpenComplaints flatId={flatId} />
    </Page>
  );
}

function DuesCard({ flatId }: { flatId: number }) {
  const summary = useFlatSummary(flatId);
  const bills = useBills(flatId);

  if (summary.isPending || bills.isPending) {
    return <Skeleton className="h-48 w-full" />;
  }
  if (summary.isError || bills.isError) {
    return (
      <ErrorState
        error={summary.error ?? bills.error}
        onRetry={() => {
          summary.refetch();
          bills.refetch();
        }}
      />
    );
  }
  return <DuesCardBody summary={summary.data} bills={bills.data} />;
}

export function nextDueDate(bills: Bill[]): string | null {
  const unpaid = bills.filter((bill) => bill.status !== "PAID").map((bill) => bill.dueDate).sort();
  return unpaid[0] ?? null;
}

function DuesCardBody({ summary, bills }: { summary: FlatSummary; bills: Bill[] }) {
  const owes = isPositive(summary.outstanding);
  const overdue = summary.daysOverdue > 0;
  const dueDate = nextDueDate(bills);

  return (
    <Card className="p-5">
      <p className="text-sm text-ink-soft">{owes ? "Maintenance due" : "Maintenance"}</p>
      {owes ? (
        <>
          <p className="mt-1 text-[34px] font-semibold leading-tight tracking-tight">
            <Money amount={summary.outstanding} />
          </p>
          {overdue ? (
            <p className="mt-1 text-sm font-medium text-danger">
              Overdue by {summary.daysOverdue} {summary.daysOverdue === 1 ? "day" : "days"}
              <span className="font-normal text-ink-soft"> · since {formatDate(summary.oldestUnpaidDueDate)}</span>
            </p>
          ) : (
            dueDate && <p className="mt-1 text-sm text-ink-soft">Due by {formatDate(dueDate)}</p>
          )}
          {isPositive(summary.accruedLateFee) && (
            <p className="mt-3 flex items-baseline justify-between border-t border-line pt-3 text-sm">
              <span className="text-ink-soft">Late fee so far, added at the next bill</span>
              <Money amount={summary.accruedLateFee} className="font-medium text-danger" />
            </p>
          )}
          <div className="mt-5 grid grid-cols-2 gap-3">
            <ButtonLink to="/bills/pay">Pay</ButtonLink>
            <ButtonLink to="/bills" variant="secondary">
              View bills
            </ButtonLink>
          </div>
        </>
      ) : (
        <>
          <p className="mt-1 text-xl font-semibold text-accent">All paid up</p>
          <p className="mt-1 text-sm text-ink-soft">
            {isPositive(summary.advance) ? (
              <>
                <Money amount={summary.advance} /> is held as advance for your next bill.
              </>
            ) : dueDate ? (
              `Next bill due ${formatDate(dueDate)}.`
            ) : (
              "Nothing is due right now."
            )}
          </p>
          <div className="mt-5">
            <ButtonLink to="/bills" variant="secondary" block>
              View bills and receipts
            </ButtonLink>
          </div>
        </>
      )}
    </Card>
  );
}

function OpenComplaints({ flatId }: { flatId: number }) {
  const complaints = useFlatComplaints(flatId);
  const open = complaints.data?.filter((complaint) => complaint.status !== "CLOSED") ?? [];

  return (
    <section aria-labelledby="open-complaints">
      <SectionTitle
        action={
          <Link to="/complaints/new" className="min-h-touch px-1 py-3 text-sm font-medium text-accent hover:underline">
            New complaint
          </Link>
        }
      >
        <span id="open-complaints">Open complaints</span>
      </SectionTitle>
      {complaints.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : complaints.isError ? (
        <ErrorState error={complaints.error} onRetry={() => complaints.refetch()} />
      ) : open.length === 0 ? (
        <EmptyState
          title="No open complaints"
          body="If something in your flat or tower needs fixing, tell the help desk in a few words."
          action={<ButtonLink to="/complaints/new" variant="secondary">Raise a complaint</ButtonLink>}
        />
      ) : (
        <ul className="space-y-3">
          {open.map((complaint) => (
            <li key={complaint.id}>
              <ComplaintCard complaint={complaint} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function ComplaintCard({ complaint }: { complaint: ComplaintSummary }) {
  return (
    <Link
      to={`/complaints/${complaint.id}`}
      className="block rounded-card border border-line bg-surface p-4 shadow-card transition-colors hover:border-line-strong"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="line-clamp-2 text-[15px] font-medium text-ink">{complaint.summary}</p>
        <StatusBadge status={complaint.status} />
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-ink-soft">
        <span>{complaint.refNo}</span>
        <span>{timeSince(complaint.createdAt)}</span>
        <span>{complaint.assigneeName ?? "Not yet assigned"}</span>
        {complaint.safetyEscalated && <Badge tone="danger">Escalated to duty manager</Badge>}
        {(complaint.linkedToRefNo || complaint.linkedCount > 0) && (
          <Badge tone="neutral">
            {complaint.linkedCount > 0
              ? `${complaint.linkedCount + 1} flats reported this`
              : `Linked to ${complaint.linkedToRefNo}`}
          </Badge>
        )}
        {complaint.status === "RESOLVED" && <Badge tone="accent">Please confirm the fix</Badge>}
      </div>
    </Link>
  );
}

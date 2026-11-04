import { useParams, useSearchParams } from "react-router-dom";
import { ButtonLink } from "../../components/Button";
import { Card } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { ErrorState, Skeleton } from "../../components/States";
import { formatDate, isPositive, titleCase } from "../../lib/format";
import { useReceipt } from "./queries";

export function ReceiptPage() {
  const { receiptNo = "" } = useParams();
  const [params] = useSearchParams();
  const justPaid = params.get("paid") === "1";
  const receipt = useReceipt(receiptNo);

  return (
    <Page title={justPaid ? "Payment received" : "Receipt"} subtitle={receiptNo} back={{ to: "/bills", label: "Bills" }}>
      {receipt.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : receipt.isError ? (
        <ErrorState error={receipt.error} onRetry={() => receipt.refetch()} />
      ) : (
        <>
          <Card className="p-5">
            {justPaid && (
              <p className="mb-4 rounded-xl bg-accent-soft px-4 py-3 text-sm text-accent">
                Thank you. Your payment is posted to flat {receipt.data.flatCode}.
              </p>
            )}
            <div className="text-center">
              <p className="text-sm text-ink-soft">Amount paid</p>
              <p className="mt-1 text-3xl font-semibold tracking-tight">
                <Money amount={receipt.data.amount} />
              </p>
              <p className="mt-1 text-sm text-ink-soft">
                {formatDate(receipt.data.paidOn)} · {titleCase(receipt.data.method)}
              </p>
            </div>
            <dl className="mt-6 space-y-2 border-t border-line pt-4 text-sm">
              <Row label="Receipt number" value={receipt.data.receiptNo} />
              <Row label="Flat" value={receipt.data.flatCode} />
              {receipt.data.reference && <Row label="Reference" value={receipt.data.reference} />}
            </dl>
            <h2 className="mt-6 text-xs font-semibold uppercase tracking-wider text-ink-faint">What it paid</h2>
            <ul className="mt-2 divide-y divide-line text-sm">
              {receipt.data.appliedTo.map((line, index) => (
                <li key={index} className="flex justify-between gap-4 py-2">
                  <span className="text-ink">{line.description}</span>
                  <Money amount={line.amount} />
                </li>
              ))}
              {isPositive(receipt.data.heldAsAdvance) && (
                <li className="flex justify-between gap-4 py-2">
                  <span className="text-ink">Held as advance for your next bill</span>
                  <Money amount={receipt.data.heldAsAdvance} />
                </li>
              )}
            </ul>
          </Card>
          <div className="mt-5 grid grid-cols-2 gap-3">
            <ButtonLink to="/home" variant="secondary">
              Home
            </ButtonLink>
            <ButtonLink to="/bills" variant="secondary">
              Bills
            </ButtonLink>
          </div>
        </>
      )}
    </Page>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-ink-soft">{label}</dt>
      <dd className="font-medium tabular-nums text-ink">{value}</dd>
    </div>
  );
}

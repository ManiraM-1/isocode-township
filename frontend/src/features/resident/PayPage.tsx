import { FormEvent, useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { formatRupees, isPositive } from "../../lib/format";
import { PaymentIntent } from "../../lib/types";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Field, inputClass } from "../../components/Field";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { ErrorState, InlineError, Skeleton } from "../../components/States";
import { useFlatSummary } from "./queries";
import { useResidentFlat } from "./ResidentLayout";

export function amountError(text: string): string | null {
  const value = text.replace(/,/g, "").trim();
  if (!value) return "Enter an amount";
  if (!/^\d+(\.\d{1,2})?$/.test(value)) return "Enter an amount in rupees, with at most two decimals";
  if (!/[1-9]/.test(value)) return "Amount must be more than zero";
  return null;
}

function newAttemptKey(): string {
  return crypto.randomUUID().replace(/-/g, "");
}

export function PayPage() {
  const { flatId, flatCode } = useResidentFlat();
  const summary = useFlatSummary(flatId);
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [failed, setFailed] = useState<PaymentIntent | null>(null);

  return (
    <Page title="Pay maintenance" subtitle={`Flat ${flatCode}`} back={{ to: "/bills", label: "Bills" }}>
      {summary.isPending ? (
        <Skeleton className="h-56 w-full" />
      ) : summary.isError ? (
        <ErrorState error={summary.error} onRetry={() => summary.refetch()} />
      ) : failed ? (
        <PaymentFailed intent={failed} onRetry={() => setFailed(null)} />
      ) : intent ? (
        <TestGateway
          intent={intent}
          onFailed={(result) => {
            setIntent(null);
            setFailed(result);
          }}
        />
      ) : (
        <AmountStep
          flatId={flatId}
          outstanding={summary.data.outstanding}
          onCreated={setIntent}
        />
      )}
    </Page>
  );
}

function AmountStep({
  flatId,
  outstanding,
  onCreated,
}: {
  flatId: number;
  outstanding: string;
  onCreated: (intent: PaymentIntent) => void;
}) {
  const [amount, setAmount] = useState(isPositive(outstanding) ? outstanding : "");
  const [touched, setTouched] = useState(false);
  const attempt = useRef<{ amount: string; key: string } | null>(null);
  const validation = amountError(amount);

  const create = useMutation({
    mutationFn: (value: string) => {
      if (!attempt.current || attempt.current.amount !== value) {
        attempt.current = { amount: value, key: newAttemptKey() };
      }
      return api.post<PaymentIntent>(`/api/flats/${flatId}/payment-intents`, {
        amount: value,
        idempotencyKey: attempt.current.key,
      });
    },
    onSuccess: onCreated,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!validation) {
      create.mutate(amount.replace(/,/g, "").trim());
    }
  }

  const serverAmountError = create.error instanceof ApiError ? create.error.fieldMessage("amount") : undefined;
  const isPartial = !validation && isPositive(outstanding) && Number(amount.replace(/,/g, "")) < Number(outstanding);

  return (
    <form onSubmit={submit} noValidate>
      <Card className="space-y-4 p-5">
        <Field
          label="Amount"
          error={(touched && validation) || serverAmountError}
          hint={
            isPositive(outstanding) ? (
              <>
                Outstanding is {formatRupees(outstanding)}. You can pay part of it.
              </>
            ) : (
              "Nothing is outstanding. Anything you pay is kept as advance for your next bill."
            )
          }
        >
          {({ id, describedBy, invalid }) => (
            <div className="relative">
              <span className="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-ink-soft">₹</span>
              <input
                id={id}
                aria-describedby={describedBy}
                aria-invalid={invalid}
                className={`${inputClass(invalid)} pl-8 text-lg font-medium tabular-nums`}
                inputMode="decimal"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                onBlur={() => setTouched(true)}
              />
            </div>
          )}
        </Field>
        {isPartial && (
          <p className="text-sm text-ink-soft">
            This pays your oldest bill first. Late interest keeps running on what is left.
          </p>
        )}
        {!serverAmountError && <InlineError error={create.error} />}
        <Button type="submit" block busy={create.isPending}>
          Continue to payment
        </Button>
      </Card>
    </form>
  );
}

function TestGateway({ intent, onFailed }: { intent: PaymentIntent; onFailed: (intent: PaymentIntent) => void }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const simulate = useMutation({
    mutationFn: (outcome: "SUCCESS" | "FAILURE") =>
      api.post<PaymentIntent>(`/api/payment-intents/${intent.id}/simulate`, { outcome }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["flat", intent.flatId] });
      if (result.status === "SUCCEEDED" && result.receiptNo) {
        navigate(`/receipts/${result.receiptNo}?paid=1`, { replace: true });
      } else {
        onFailed(result);
      }
    },
    onError: setError,
  });

  useEffect(() => setError(null), [intent.id]);

  return (
    <Card className="overflow-hidden">
      <div className="bg-caution-soft px-5 py-2.5 text-center text-xs font-semibold uppercase tracking-widest text-caution">
        Test mode · no real money moves
      </div>
      <div className="space-y-5 p-5">
        <div className="text-center">
          <p className="text-sm text-ink-soft">Test payment gateway</p>
          <p className="mt-1 text-3xl font-semibold tracking-tight">
            <Money amount={intent.amount} />
          </p>
          <p className="mt-1 text-xs tabular-nums text-ink-faint">{intent.gatewayRef}</p>
        </div>
        <InlineError error={error} />
        <div className="space-y-3">
          <Button block busy={simulate.isPending && simulate.variables === "SUCCESS"} disabled={simulate.isPending}
            onClick={() => simulate.mutate("SUCCESS")}>
            Pay successfully
          </Button>
          <Button block variant="secondary" busy={simulate.isPending && simulate.variables === "FAILURE"}
            disabled={simulate.isPending} onClick={() => simulate.mutate("FAILURE")}>
            Simulate failure
          </Button>
        </div>
      </div>
    </Card>
  );
}

function PaymentFailed({ intent, onRetry }: { intent: PaymentIntent; onRetry: () => void }) {
  return (
    <Card role="alert" className="p-6 text-center">
      <p className="text-lg font-semibold text-ink">Payment failed</p>
      <p className="mx-auto mt-1 max-w-xs text-sm text-ink-soft">
        The gateway declined <Money amount={intent.amount} />. Nothing was charged and your dues are unchanged.
      </p>
      <div className="mt-5">
        <Button onClick={onRetry}>Try again</Button>
      </div>
    </Card>
  );
}

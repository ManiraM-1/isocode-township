import { ClipboardEvent, FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { maskPhone } from "../../lib/phone";
import { homePathFor, ME_KEY } from "../../lib/session";
import { Me } from "../../lib/types";
import { Button } from "../../components/Button";
import { InlineError } from "../../components/States";

const CODE_LENGTH = 6;
const RESEND_AFTER_SECONDS = 30;

type OtpStepProps = {
  phone: string;
  expiresInSeconds: number;
  demoOtp?: string;
  onResend: () => Promise<unknown>;
  onChangeNumber: () => void;
};

export function OtpStep({ phone, demoOtp, onResend, onChangeNumber }: OtpStepProps) {
  const [digits, setDigits] = useState<string[]>(Array(CODE_LENGTH).fill(""));
  const [secondsLeft, setSecondsLeft] = useState(RESEND_AFTER_SECONDS);
  const [resending, setResending] = useState(false);
  const [resendError, setResendError] = useState<unknown>(null);
  const boxes = useRef<(HTMLInputElement | null)[]>([]);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const verify = useMutation({
    mutationFn: (code: string) => api.post<Me>("/api/auth/otp/verify", { phone, code }),
    onSuccess: (me) => {
      queryClient.setQueryData(ME_KEY, me);
      navigate(homePathFor(me), { replace: true });
    },
    onError: () => {
      setDigits(Array(CODE_LENGTH).fill(""));
      boxes.current[0]?.focus();
    },
  });

  useEffect(() => {
    boxes.current[0]?.focus();
  }, []);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setTimeout(() => setSecondsLeft((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [secondsLeft]);

  const code = digits.join("");

  function submit(event?: FormEvent) {
    event?.preventDefault();
    if (code.length === CODE_LENGTH && !verify.isPending) {
      verify.mutate(code);
    }
  }

  function setDigit(index: number, value: string) {
    const digit = value.replace(/\D/g, "").slice(-1);
    const next = [...digits];
    next[index] = digit;
    setDigits(next);
    if (digit && index < CODE_LENGTH - 1) {
      boxes.current[index + 1]?.focus();
    }
    if (next.every(Boolean)) {
      verify.mutate(next.join(""));
    }
  }

  function onKeyDown(index: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Backspace" && !digits[index] && index > 0) {
      boxes.current[index - 1]?.focus();
    }
  }

  function onPaste(event: ClipboardEvent<HTMLInputElement>) {
    const pasted = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, CODE_LENGTH);
    if (!pasted) return;
    event.preventDefault();
    const next = Array.from({ length: CODE_LENGTH }, (_, index) => pasted[index] ?? "");
    setDigits(next);
    boxes.current[Math.min(pasted.length, CODE_LENGTH - 1)]?.focus();
    if (pasted.length === CODE_LENGTH) {
      verify.mutate(pasted);
    }
  }

  async function resend() {
    setResending(true);
    setResendError(null);
    try {
      await onResend();
      verify.reset();
      setDigits(Array(CODE_LENGTH).fill(""));
      setSecondsLeft(RESEND_AFTER_SECONDS);
      boxes.current[0]?.focus();
    } catch (error) {
      setResendError(error);
    } finally {
      setResending(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <p className="text-sm text-ink-soft">
        Sent to <span className="font-medium tabular-nums text-ink">{maskPhone(phone)}</span>{" "}
        <button type="button" onClick={onChangeNumber} className="min-h-touch px-1 font-medium text-accent hover:underline">
          Change
        </button>
      </p>

      {demoOtp && (
        <p className="rounded-xl bg-accent-soft px-4 py-3 text-sm text-accent">
          Demo mode: your code is <span className="font-semibold tracking-widest tabular-nums">{demoOtp}</span>
        </p>
      )}

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-ink">6-digit code</legend>
        <div className="flex justify-between gap-2">
          {digits.map((digit, index) => (
            <input
              key={index}
              ref={(element) => {
                boxes.current[index] = element;
              }}
              aria-label={`Digit ${index + 1}`}
              className="h-14 w-full min-w-0 rounded-xl border border-line-strong bg-surface text-center text-xl font-semibold tabular-nums text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
              inputMode="numeric"
              autoComplete={index === 0 ? "one-time-code" : "off"}
              maxLength={CODE_LENGTH}
              value={digit}
              disabled={verify.isPending}
              onChange={(event) => setDigit(index, event.target.value)}
              onKeyDown={(event) => onKeyDown(index, event)}
              onPaste={onPaste}
            />
          ))}
        </div>
      </fieldset>

      <VerifyError error={verify.error} />
      <InlineError error={resendError} />

      <Button type="submit" block busy={verify.isPending} disabled={code.length < CODE_LENGTH}>
        Verify and sign in
      </Button>

      <p className="text-center text-sm text-ink-soft">
        {secondsLeft > 0 ? (
          <span className="tabular-nums">Resend code in 0:{String(secondsLeft).padStart(2, "0")}</span>
        ) : (
          <Button variant="ghost" size="sm" onClick={resend} busy={resending}>
            Resend code
          </Button>
        )}
      </p>
    </form>
  );
}

function VerifyError({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) {
    return <InlineError error={error} />;
  }
  const remaining = error.body.attemptsRemaining;
  return (
    <p role="alert" className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
      {error.message}
      {typeof remaining === "number" &&
        ` ${remaining === 1 ? "1 attempt" : `${remaining} attempts`} left before the code is locked.`}
    </p>
  );
}

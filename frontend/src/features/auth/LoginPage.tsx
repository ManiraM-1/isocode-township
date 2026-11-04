import { FormEvent, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api, ApiError } from "../../lib/api";
import { PHONE_PREFIX, phoneError, toE164 } from "../../lib/phone";
import { useDemoUsers } from "../../lib/session";
import { DemoUser } from "../../lib/types";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Field";
import { InlineError } from "../../components/States";
import { OtpStep } from "./OtpStep";

type OtpRequested = { phone: string; expiresInSeconds: number; demoOtp?: string };

export function LoginPage() {
  const [requested, setRequested] = useState<OtpRequested | null>(null);

  const requestOtp = useMutation({
    mutationFn: async (phone: string) => {
      const response = await api.post<{ expiresInSeconds: number; demoOtp?: string }>("/api/auth/otp/request", {
        phone,
      });
      return { phone, ...response };
    },
    onSuccess: setRequested,
  });

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center px-4 py-10">
        <div className="mb-8">
          <div aria-hidden className="mb-5 grid h-11 w-11 place-items-center rounded-xl bg-accent text-lg font-semibold text-white">
            T
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">
            {requested ? "Enter the code" : "Sign in to your township"}
          </h1>
          <p className="mt-1 text-sm text-ink-soft">
            {requested ? "We sent a 6-digit code by SMS." : "Use the mobile number registered with your flat."}
          </p>
        </div>
        {requested ? (
          <OtpStep
            phone={requested.phone}
            expiresInSeconds={requested.expiresInSeconds}
            demoOtp={requested.demoOtp}
            onResend={() => requestOtp.mutateAsync(requested.phone)}
            onChangeNumber={() => {
              requestOtp.reset();
              setRequested(null);
            }}
          />
        ) : (
          <PhoneStep requesting={requestOtp.isPending} error={requestOtp.error} onSubmit={requestOtp.mutate} />
        )}
      </main>
    </div>
  );
}

function PhoneStep({
  requesting,
  error,
  onSubmit,
}: {
  requesting: boolean;
  error: unknown;
  onSubmit: (phone: string) => void;
}) {
  const [phone, setPhone] = useState("");
  const [touched, setTouched] = useState(false);
  const demoUsers = useDemoUsers();
  const validation = phoneError(phone);
  const serverFieldError = error instanceof ApiError ? error.fieldMessage("phone") : undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!validation) {
      onSubmit(toE164(phone));
    }
  }

  function pick(user: DemoUser) {
    setPhone(user.phone.slice(PHONE_PREFIX.length));
    onSubmit(user.phone);
  }

  return (
    <>
      <form onSubmit={submit} noValidate className="space-y-4">
        <Field label="Mobile number" error={(touched && validation) || serverFieldError}>
          {({ id, describedBy, invalid }) => (
            <div className="flex gap-2">
              <span className="grid min-h-touch place-items-center rounded-xl border border-line-strong bg-canvas px-3 text-[15px] text-ink-soft">
                {PHONE_PREFIX}
              </span>
              <input
                id={id}
                aria-describedby={describedBy}
                aria-invalid={invalid}
                className={`${inputClass(invalid)} tabular-nums`}
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder="98765 43210"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                onBlur={() => setTouched(true)}
              />
            </div>
          )}
        </Field>
        {!serverFieldError && <InlineError error={error} />}
        <Button type="submit" block busy={requesting}>
          Send code
        </Button>
      </form>

      {demoUsers.data && demoUsers.data.length > 0 && (
        <section className="mt-10" aria-labelledby="demo-heading">
          <h2 id="demo-heading" className="text-xs font-semibold uppercase tracking-wider text-ink-faint">
            Demo accounts
          </h2>
          <ul className="mt-2 divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {demoUsers.data.map((user) => (
              <li key={user.phone}>
                <button
                  type="button"
                  disabled={requesting}
                  onClick={() => pick(user)}
                  className="flex min-h-touch w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-canvas disabled:opacity-50"
                >
                  <span>
                    <span className="block text-sm font-medium text-ink">
                      {user.name}
                      {user.flatCode && <span className="font-normal text-ink-soft"> · {user.flatCode}</span>}
                    </span>
                    <span className="block text-xs text-ink-faint">{user.note}</span>
                  </span>
                  <span aria-hidden className="text-ink-faint">
                    →
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

import { Component, ReactNode } from "react";
import { Button } from "./Button";

type State = { failed: boolean };

export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidUpdate(previous: { resetKey?: string }) {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) {
      this.setState({ failed: false });
    }
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <main className="mx-auto max-w-md px-4 pt-16">
        <div role="alert" className="rounded-card border border-danger/30 bg-danger-soft/50 px-6 py-8 text-center">
          <p className="font-medium text-ink">This screen ran into a problem</p>
          <p className="mt-1 text-sm text-ink-soft">Nothing you saved is lost. Reload to try again.</p>
          <div className="mt-5 flex justify-center">
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Reload
            </Button>
          </div>
        </div>
      </main>
    );
  }
}

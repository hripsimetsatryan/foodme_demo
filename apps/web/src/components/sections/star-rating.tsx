import { useRef, type KeyboardEvent } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

const STARS = [1, 2, 3, 4, 5] as const;

/** Read-only stars, announced as one image to screen readers (R13, R17, R24). */
export function StarRatingDisplay({
  value,
  size = 16,
  className,
}: {
  value: number;
  size?: number;
  className?: string;
}) {
  return (
    <span
      role="img"
      aria-label={`Rated ${value} out of 5 stars`}
      className={cn("inline-flex items-center gap-0.5", className)}
    >
      {STARS.map((star) => (
        <Star
          key={star}
          size={size}
          aria-hidden="true"
          strokeWidth={1.75}
          className={star <= value ? "text-amber-500" : "text-zinc-300"}
          fill={star <= value ? "currentColor" : "none"}
        />
      ))}
    </span>
  );
}

interface StarRatingInputProps {
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  labelledBy?: string;
}

/**
 * 1–5 star picker (R16, R24). A WAI-ARIA radio group: mouse/touch tap a star,
 * keyboard uses Tab to enter and arrow keys / Home / End to change the value.
 */
export function StarRatingInput({ value, onChange, disabled, labelledBy }: StarRatingInputProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const focusableStar = value > 0 ? value : 1;

  const select = (star: number) => {
    onChange(star);
    refs.current[star - 1]?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, star: number) => {
    let next: number | null = null;
    if (event.key === "ArrowRight" || event.key === "ArrowUp") next = Math.min(5, star + 1);
    else if (event.key === "ArrowLeft" || event.key === "ArrowDown") next = Math.max(1, star - 1);
    else if (event.key === "Home") next = 1;
    else if (event.key === "End") next = 5;
    if (next !== null) {
      event.preventDefault();
      select(next);
    }
  };

  return (
    <div
      role="radiogroup"
      aria-labelledby={labelledBy}
      aria-required="true"
      className="flex items-center gap-1"
    >
      {STARS.map((star) => {
        const filled = star <= value;
        return (
          <button
            key={star}
            ref={(el) => {
              refs.current[star - 1] = el;
            }}
            type="button"
            role="radio"
            aria-checked={value === star}
            aria-label={`${star} ${star === 1 ? "star" : "stars"}`}
            tabIndex={star === focusableStar ? 0 : -1}
            disabled={disabled}
            onClick={() => select(star)}
            onKeyDown={(event) => handleKeyDown(event, star)}
            className={cn(
              "flex h-11 w-11 items-center justify-center rounded-full",
              "transition-transform duration-150 hover:scale-110 active:scale-95",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900 focus-visible:ring-offset-2",
              "disabled:cursor-not-allowed disabled:opacity-50",
            )}
          >
            <Star
              size={28}
              aria-hidden="true"
              strokeWidth={1.5}
              className={filled ? "text-amber-500" : "text-zinc-300"}
              fill={filled ? "currentColor" : "none"}
            />
          </button>
        );
      })}
    </div>
  );
}

import { useId, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { foodmeApi } from "@/api/foodme";
import { ApiRequestError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { StarRatingInput } from "@/components/sections/star-rating";
import { cn } from "@/lib/utils";
import type { FullOrderDto, OrderListResponseDto } from "@/types";

const MAX_COMMENT_LENGTH = 1000;

interface RateOrderDialogProps {
  order: FullOrderDto;
  onClose: () => void;
}

/** "Rate order" window from My Orders (R16–R18). Mount it only while open. */
export function RateOrderDialog({ order, onClose }: RateOrderDialogProps) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const starsLabelId = useId();
  const commentId = useId();
  const counterId = useId();

  const mutation = useMutation({
    mutationFn: () =>
      foodmeApi.reviewOrder(order.number, {
        rating,
        comment: comment.trim() === "" ? undefined : comment.trim(),
      }),
    onSuccess: (review) => {
      // R17: swap the button for the stars immediately, then refetch for the truth.
      queryClient.setQueriesData<OrderListResponseDto>({ queryKey: ["my-orders"] }, (old) =>
        old
          ? { ...old, list: old.list.map((o) => (o.number === order.number ? { ...o, review } : o)) }
          : old,
      );
      void queryClient.invalidateQueries({ queryKey: ["my-orders"] });
      void queryClient.invalidateQueries({ queryKey: ["order", order.number] });
      // R12: the chef's new average shows on the storefront straight away.
      void queryClient.invalidateQueries({ queryKey: ["chef"] });
      void queryClient.invalidateQueries({ queryKey: ["chefs"] });
      onClose();
    },
  });

  const errorMessage = mutation.isError
    ? mutation.error instanceof ApiRequestError && mutation.error.message
      ? mutation.error.message
      : "We couldn’t save your rating. Please try again."
    : null;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (rating < 1 || mutation.isPending) return;
    mutation.mutate();
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !mutation.isPending) onClose();
      }}
    >
      <DialogContent className="p-6 sm:p-7">
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="pr-10">
            <DialogTitle className="font-display text-xl font-bold text-zinc-900">
              Rate order {order.number}
            </DialogTitle>
            <DialogDescription className="mt-1 text-sm text-zinc-500">
              How was your food from {order.chefName}?
            </DialogDescription>
          </div>

          <div>
            <p id={starsLabelId} className="mb-1 text-sm font-semibold text-zinc-800">
              Your rating
            </p>
            <StarRatingInput
              value={rating}
              onChange={setRating}
              disabled={mutation.isPending}
              labelledBy={starsLabelId}
            />
          </div>

          <div>
            <label htmlFor={commentId} className="mb-1.5 block text-sm font-semibold text-zinc-800">
              Comment <span className="font-normal text-zinc-400">(optional)</span>
            </label>
            <textarea
              id={commentId}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="Tell us about your order"
              maxLength={MAX_COMMENT_LENGTH}
              rows={4}
              disabled={mutation.isPending}
              aria-describedby={counterId}
              className={cn(
                "w-full resize-y rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm text-zinc-900",
                "placeholder:text-zinc-400 hover:border-zinc-300",
                "focus-visible:outline-none focus-visible:border-zinc-400 focus-visible:ring-4 focus-visible:ring-zinc-100",
                "disabled:cursor-not-allowed disabled:opacity-50",
              )}
            />
            <p id={counterId} className="mt-1 text-right text-xs tabular-nums text-zinc-400">
              {comment.length}/{MAX_COMMENT_LENGTH}
            </p>
          </div>

          {errorMessage && (
            <p role="alert" className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
              {errorMessage}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              className="rounded-full"
              onClick={onClose}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" className="rounded-full" disabled={rating < 1 || mutation.isPending}>
              {mutation.isPending ? "Saving…" : errorMessage ? "Try again" : "Submit rating"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

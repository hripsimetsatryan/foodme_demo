import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * TEST-11 Order ratings - storefront (spec R14-R19, R24, R25; acceptance criteria 1 and 2).
 * Orders are created through the API and moved to DELIVERED by the admin API,
 * then the customer rates them in the real UI.
 */
const API = process.env.VITE_API_BASE_URL || "http://localhost:8081";
const AUTH_KEY = "foodme.customer.auth"; // web/src/lib/auth-storage.ts

type Auth = { token: string; customer: { email: string } };
type Order = { id: number; number: string };

async function firstChefAndDish(request: APIRequestContext) {
  const chefs = await (await request.get(`${API}/api/chef/active?page=0&size=12`)).json();
  const chef = chefs.exploreChefResponseDtoList[0];
  const detail = await (await request.get(`${API}/api/chef/${chef.id}`)).json();
  return { chefId: chef.id as number, dishId: detail.dishes[0].id as number };
}

async function registerCustomer(request: APIRequestContext): Promise<Auth> {
  const email = `rating-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const res = await request.post(`${API}/api/auth/register`, {
    data: { fullName: "Rating Customer", email, phoneNumber: "+37490000011", password: "secret123" },
  });
  expect(res.ok()).toBeTruthy();
  return res.json();
}

async function placeOrder(request: APIRequestContext, auth: Auth): Promise<Order> {
  const { chefId, dishId } = await firstChefAndDish(request);
  const res = await request.post(`${API}/api/order`, {
    headers: { Authorization: `Bearer ${auth.token}` },
    data: {
      chefId,
      receiverName: "Rating Customer",
      receiverPhoneNumber: "+37490000011",
      receiverEmail: auth.customer.email,
      paymentType: "CASH",
      deliveryMethod: "TAKEAWAY",
      note: "order rating e2e",
      createOrderDishes: [{ dishId, quantity: 1 }],
    },
  });
  expect(res.ok()).toBeTruthy();
  const { number } = await res.json();
  // The create response has no database id; the order details endpoint has it (admin API needs it).
  const details = await request.get(`${API}/api/order/number/${number}`);
  expect(details.ok()).toBeTruthy();
  return details.json();
}

async function markDelivered(request: APIRequestContext, order: Order) {
  const login = await request.post(`${API}/admin/auth/login`, {
    data: { username: "admin", password: "admin123" },
  });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  for (const status of ["ACCEPTED", "DELIVERED"]) {
    const res = await request.patch(`${API}/admin/order/${order.id}/status`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { status },
    });
    expect(res.ok(), `move order to ${status}`).toBeTruthy();
  }
}

async function signIn(page: Page, auth: Auth) {
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key, value),
    [AUTH_KEY, JSON.stringify(auth)] as const,
  );
}

async function openMyOrders(page: Page, order: Order) {
  await page.goto("/orders");
  await expect(page.getByRole("heading", { name: "Your orders" })).toBeVisible();
  await expect(page.getByText(order.number)).toBeVisible({ timeout: 15000 });
}

test.describe("Order ratings - My Orders", () => {
  test("customer rates a delivered order and still sees it after reload (AC1, R14, R16, R17, R19)", async ({
    page,
    request,
  }) => {
    const auth = await registerCustomer(request);
    const order = await placeOrder(request, auth);
    await markDelivered(request, order);
    await signIn(page, auth);
    await openMyOrders(page, order);

    await page.getByRole("button", { name: `Rate order ${order.number}` }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: `Rate order ${order.number}` })).toBeVisible();
    await expect(dialog.getByRole("radio")).toHaveCount(5);
    await expect(dialog.getByPlaceholder("Tell us about your order")).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Submit rating" })).toBeDisabled();

    await dialog.getByRole("radio", { name: "4 stars" }).click();
    await dialog.getByPlaceholder("Tell us about your order").fill("Warm and on time");
    await dialog.getByRole("button", { name: "Submit rating" }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByRole("img", { name: "Rated 4 out of 5 stars" })).toBeVisible();
    await expect(page.getByRole("button", { name: `Rate order ${order.number}` })).toHaveCount(0);

    await page.reload();
    await expect(page.getByRole("img", { name: "Rated 4 out of 5 stars" })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: `Rate order ${order.number}` })).toHaveCount(0);

    // R13: the tracking page shows the rating and the comment too
    await page.goto(`/tracking/${order.number}`);
    await expect(page.getByRole("img", { name: "Rated 4 out of 5 stars" })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText("Warm and on time")).toBeVisible();
  });

  test("an order that is not delivered has no rating option (AC2, R15)", async ({ page, request }) => {
    const auth = await registerCustomer(request);
    const order = await placeOrder(request, auth); // stays NEW
    await signIn(page, auth);
    await openMyOrders(page, order);

    await expect(page.getByRole("button", { name: /Rate order/ })).toHaveCount(0);
    await expect(page.getByRole("img", { name: /Rated \d out of 5 stars/ })).toHaveCount(0);
  });

  test("stars work with the keyboard and are announced to screen readers (R24)", async ({ page, request }) => {
    const auth = await registerCustomer(request);
    const order = await placeOrder(request, auth);
    await markDelivered(request, order);
    await signIn(page, auth);
    await openMyOrders(page, order);

    await page.getByRole("button", { name: `Rate order ${order.number}` }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("radiogroup", { name: "Your rating" })).toBeVisible();

    await dialog.getByRole("radio", { name: "1 star" }).focus();
    await page.keyboard.press("ArrowRight");
    await page.keyboard.press("ArrowRight");
    await expect(dialog.getByRole("radio", { name: "3 stars" })).toHaveAttribute("aria-checked", "true");
    await expect(dialog.getByRole("radio", { name: "3 stars" })).toBeFocused();
    await page.keyboard.press("End");
    await expect(dialog.getByRole("radio", { name: "5 stars" })).toHaveAttribute("aria-checked", "true");
    await page.keyboard.press("Home");
    await expect(dialog.getByRole("radio", { name: "1 star" })).toHaveAttribute("aria-checked", "true");
    await expect(dialog.getByRole("button", { name: "Submit rating" })).toBeEnabled();
  });

  test("a failed save shows the reason and lets the customer try again (R18)", async ({ page, request }) => {
    const auth = await registerCustomer(request);
    const order = await placeOrder(request, auth);
    await markDelivered(request, order);
    await signIn(page, auth);
    await openMyOrders(page, order);

    // First attempt fails, the second one reaches the real backend.
    await page.route(
      "**/api/customer/orders/*/review",
      (route) => route.fulfill({ status: 503, json: { message: "Ratings are temporarily unavailable" } }),
      { times: 1 },
    );

    await page.getByRole("button", { name: `Rate order ${order.number}` }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByRole("radio", { name: "5 stars" }).click();
    await dialog.getByRole("button", { name: "Submit rating" }).click();

    await expect(dialog.getByRole("alert")).toHaveText("Ratings are temporarily unavailable");
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("radio", { name: "5 stars" })).toHaveAttribute("aria-checked", "true");

    await dialog.getByRole("button", { name: "Try again" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole("img", { name: "Rated 5 out of 5 stars" })).toBeVisible();
  });
});

test.describe("Order ratings - mobile (R25)", () => {
  test.use({ viewport: { width: 375, height: 740 } });

  test("rating window fits a phone screen", async ({ page, request }) => {
    const auth = await registerCustomer(request);
    const order = await placeOrder(request, auth);
    await markDelivered(request, order);
    await signIn(page, auth);
    await openMyOrders(page, order);

    await page.getByRole("button", { name: `Rate order ${order.number}` }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    for (const name of ["1 star", "5 stars"]) {
      await expect(dialog.getByRole("radio", { name })).toBeInViewport();
    }
    const box = await dialog.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(375);

    await dialog.getByRole("radio", { name: "2 stars" }).click();
    await dialog.getByRole("button", { name: "Submit rating" }).click();
    await expect(page.getByRole("img", { name: "Rated 2 out of 5 stars" })).toBeVisible();
  });
});

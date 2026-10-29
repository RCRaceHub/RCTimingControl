import { test, expect } from '@playwright/test';

const ADMIN_EMAIL = 'admin@example.com';
const RACER_EMAIL = 'dave.racer@example.com';
const PASSWORD = 'trial123';

test.describe('Public pages', () => {
  test('login page renders', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('about page shows version', async ({ page }) => {
    await page.goto('/about');
    await expect(page.getByText('RC Timing Control')).toBeVisible();
    await expect(page.getByText(/v0\./)).toBeVisible();
    await expect(page.getByText(/early pre-release/i)).toBeVisible();
  });

  test('public event schedule loads without login', async ({ page }) => {
    await page.goto('/events');
    await expect(page).not.toHaveURL(/login/);
    await expect(page.getByRole('heading', { name: /event/i })).toBeVisible();
  });
});

test.describe('Admin login and navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(ADMIN_EMAIL);
    await page.getByLabel(/password/i).fill(PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL(/\/admin\//);
  });

  test('admin reaches events list', async ({ page }) => {
    await expect(page).toHaveURL(/\/admin\/events/);
    await expect(page.getByRole('heading', { name: /event/i })).toBeVisible();
  });

  test('admin can open championship list', async ({ page }) => {
    await page.getByRole('link', { name: /championship/i }).click();
    await page.waitForURL(/\/admin\/championships/);
    await expect(page.getByRole('heading', { name: /championship/i })).toBeVisible();
  });

  test('admin can open club profile', async ({ page }) => {
    await page.getByRole('link', { name: /club/i }).click();
    await page.waitForURL(/\/admin\/club/);
    await expect(page.getByRole('heading', { name: 'Club Profile', level: 1 })).toBeVisible();
  });

  test('admin can open decoder settings page', async ({ page }) => {
    await page.goto('/admin/decoder');
    await expect(page.getByRole('heading', { name: /decoder/i })).toBeVisible();
  });
});

test.describe('Officials-only sign-in', () => {
  test('a racer cannot sign in', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(RACER_EMAIL);
    await page.getByLabel(/password/i).fill(PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('racer portal and self-registration pages are gone', async ({ page }) => {
    for (const path of ['/register', '/forgot-password', '/racer/profile']) {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    }
  });
});

test.describe('Race control access', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(ADMIN_EMAIL);
    await page.getByLabel(/password/i).fill(PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL(/\/admin\//);
  });

  test('race control selector page loads', async ({ page }) => {
    await page.goto('/admin/race-control');
    await expect(page.getByRole('heading', { name: /race control/i })).toBeVisible();
  });
});

test.describe('Auth guards', () => {
  test('protected admin route redirects to login when not authenticated', async ({ page }) => {
    await page.goto('/admin/events');
    await expect(page).toHaveURL(/\/login/);
  });
});

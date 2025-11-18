// @ts-check
import { test, expect } from '@playwright/test';

/**
 * E2E tests for stateful parameterized notebooks.
 * Tests the full POST→redirect→render flow with state management.
 *
 * Prerequisites:
 * - Clay server running on localhost:1971
 * - testnotebook.clj in notebooks directory
 *
 * Run with: npx playwright test test-stateful-params.spec.js
 */

const BASE_URL = 'http://localhost:1971';
const TEST_PAGE = 'testnotebook.html';

// Helper to extract state-id from URL
function getStateId(url) {
  const match = url.match(/\/app\/[^/]+\/([a-f0-9-]+)$/);
  return match ? match[1] : null;
}

test.describe('Stateful Parameterized Notebooks', () => {

  test.describe('Initial POST Flow', () => {

    test('POST creates state and redirects to UUID URL', async ({ page }) => {
      // Create a form and submit it
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1test123">
          <button type="submit">Submit</button>
        </form>
      `);

      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // URL should match /app/testnotebook.html/{uuid}
      const url = page.url();
      expect(url).toMatch(/\/app\/testnotebook\.html\/[a-f0-9-]{36}$/);

      // State-id should be a valid UUID
      const stateId = getStateId(url);
      expect(stateId).toBeTruthy();
      expect(stateId.length).toBe(36);
    });

    test('Params are displayed in rendered page', async ({ page }) => {
      // Submit POST with params
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1wallet456">
          <input name="filter" value="active">
          <button type="submit">Submit</button>
        </form>
      `);

      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // Verify params are displayed
      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('pb1wallet456');
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('active');
    });

    test('Multiple params submitted together', async ({ page }) => {
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1multi">
          <input name="filter" value="staked">
          <input name="sort" value="asc">
          <button type="submit">Submit</button>
        </form>
      `);

      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('pb1multi');
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('staked');
      await expect(page.locator('[data-testid="param-sort"]')).toHaveText('asc');
    });
  });

  test.describe('Form Submissions', () => {

    test('Form submission creates new state', async ({ page }) => {
      // First, create initial state with wallet
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1initial">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      const firstUrl = page.url();
      const firstStateId = getStateId(firstUrl);

      // Now use the page's form to add filter
      await page.selectOption('[data-testid="select-filter"]', 'active');
      await page.click('[data-testid="submit-filter"]');
      await page.waitForLoadState('networkidle');

      // Should have new state-id
      const secondUrl = page.url();
      const secondStateId = getStateId(secondUrl);
      expect(secondStateId).not.toBe(firstStateId);

      // Filter should be present in new state
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('active');
    });

    test('Form submission overrides existing param', async ({ page }) => {
      // Create initial state with wallet
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1old">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // Submit new wallet via the page's form
      await page.fill('[data-testid="input-wallet"]', 'pb1new');
      await page.click('[data-testid="submit-wallet"]');
      await page.waitForLoadState('networkidle');

      // Wallet should be updated
      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('pb1new');
    });
  });

  test.describe('Link Click Conversion', () => {

    test('Link with params converts to POST', async ({ page }) => {
      // Create initial state
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1linktest">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      const firstStateId = getStateId(page.url());

      // Click link that adds filter param
      await page.click('[data-testid="link-filter-active"]');
      await page.waitForLoadState('networkidle');

      // Should have new state-id (link was converted to POST)
      const secondStateId = getStateId(page.url());
      expect(secondStateId).not.toBe(firstStateId);

      // Filter should be present in new state
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('active');
    });

    test('Link with multiple params creates state', async ({ page }) => {
      // Create initial state with sort
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="sort" value="desc">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // Click link with wallet and filter
      await page.click('[data-testid="link-multi-params"]');
      await page.waitForLoadState('networkidle');

      // Link's params should be present in new state
      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('pb1test');
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('staked');
    });
  });

  test.describe('State Independence', () => {

    test('Each POST creates unique state-id', async ({ page }) => {
      const stateIds = [];

      // Create multiple states
      for (let i = 0; i < 3; i++) {
        await page.setContent(`
          <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
            <input name="index" value="${i}">
            <button type="submit">Submit</button>
          </form>
        `);
        await page.click('button[type="submit"]');
        await page.waitForLoadState('networkidle');
        stateIds.push(getStateId(page.url()));
      }

      // All state-ids should be unique
      const uniqueIds = new Set(stateIds);
      expect(uniqueIds.size).toBe(3);
    });

    test('Multiple tabs have independent states', async ({ browser }) => {
      const context = await browser.newContext();
      const page1 = await context.newPage();
      const page2 = await context.newPage();

      // Tab 1: Create state with wallet1
      await page1.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="wallet1">
          <button type="submit">Submit</button>
        </form>
      `);
      await page1.click('button[type="submit"]');
      await page1.waitForLoadState('networkidle');

      // Tab 2: Create state with wallet2
      await page2.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="wallet2">
          <button type="submit">Submit</button>
        </form>
      `);
      await page2.click('button[type="submit"]');
      await page2.waitForLoadState('networkidle');

      // Verify they have different state-ids
      const stateId1 = getStateId(page1.url());
      const stateId2 = getStateId(page2.url());
      expect(stateId1).not.toBe(stateId2);

      // Verify they show different wallets
      await expect(page1.locator('[data-testid="param-wallet"]')).toHaveText('wallet1');
      await expect(page2.locator('[data-testid="param-wallet"]')).toHaveText('wallet2');

      await context.close();
    });
  });

  test.describe('URL Patterns', () => {

    test('State URL follows /app/page.html/{uuid} pattern', async ({ page }) => {
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="test" value="pattern">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      const url = page.url();
      // Full pattern: http://localhost:1971/app/testnotebook.html/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
      expect(url).toMatch(new RegExp(
        `^${BASE_URL}/app/testnotebook\\.html/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$`
      ));
    });

    test('Invalid state-id returns 404', async ({ page }) => {
      const response = await page.goto(`${BASE_URL}/app/${TEST_PAGE}/invalid-state-id-12345`);
      expect(response.status()).toBe(404);
    });
  });

  test.describe('Browser Navigation', () => {

    test('Back button returns to previous state', async ({ page }) => {
      // Create first state
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1first">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');
      const firstStateId = getStateId(page.url());

      // Add filter param
      await page.click('[data-testid="link-filter-active"]');
      await page.waitForLoadState('networkidle');
      const secondStateId = getStateId(page.url());

      expect(secondStateId).not.toBe(firstStateId);

      // Go back
      await page.goBack();
      await page.waitForLoadState('networkidle');

      // Should be back at first state
      const backStateId = getStateId(page.url());
      expect(backStateId).toBe(firstStateId);

      // Filter should not be present
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('not-set');
    });
  });

  test.describe('Edge Cases', () => {

    test('Empty params renders without state', async ({ page }) => {
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // Without params, server renders page directly (no state created)
      expect(page.url()).toBe(`${BASE_URL}/${TEST_PAGE}`);

      // All params should be not-set
      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('not-set');
      await expect(page.locator('[data-testid="param-filter"]')).toHaveText('not-set');
      await expect(page.locator('[data-testid="param-sort"]')).toHaveText('not-set');
    });

    test('Special characters in params are preserved', async ({ page }) => {
      await page.setContent(`
        <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
          <input name="wallet" value="pb1test@#$%">
          <button type="submit">Submit</button>
        </form>
      `);
      await page.click('button[type="submit"]');
      await page.waitForLoadState('networkidle');

      // Special chars should be preserved
      await expect(page.locator('[data-testid="param-wallet"]')).toHaveText('pb1test@#$%');
    });
  });
});

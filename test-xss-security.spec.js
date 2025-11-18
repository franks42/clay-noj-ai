// @ts-check
import { test, expect } from '@playwright/test';

/**
 * XSS Security Tests for Clay Hiccup Rendering
 *
 * These tests verify that user-provided parameters are properly escaped
 * to prevent Cross-Site Scripting (XSS) attacks.
 *
 * The vulnerability: Hiccup does not escape strings by default, so
 * user input like "<script>alert()</script>" renders as executable HTML.
 *
 * Run with: npx playwright test test-xss-security.spec.js
 */

const BASE_URL = 'http://localhost:1971';
const TEST_PAGE = 'testnotebook.html';

test.describe('XSS Security - HTML Injection Prevention', () => {

  test('Script tags in params should be escaped, not executed', async ({ page }) => {
    // This is the critical XSS test
    const maliciousPayload = '<script>alert(1)</script>';

    await page.setContent(`
      <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
        <input name="wallet" value="${maliciousPayload}">
        <button type="submit">Submit</button>
      </form>
    `);

    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    // Get the raw HTML of the param display
    const paramElement = page.locator('[data-testid="param-wallet"]');
    const innerHTML = await paramElement.innerHTML();

    // The script tag should be escaped as &lt;script&gt;, NOT rendered as <script>
    // If this fails, it means XSS vulnerability exists
    expect(innerHTML).not.toContain('<script>');
    expect(innerHTML).toContain('&lt;script&gt;');
    expect(innerHTML).toContain('&lt;/script&gt;');
  });

  test('HTML tags in params should be escaped', async ({ page }) => {
    const htmlPayload = '<div onclick="alert()">click me</div>';

    await page.setContent(`
      <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
        <input name="wallet" value='${htmlPayload}'>
        <button type="submit">Submit</button>
      </form>
    `);

    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const paramElement = page.locator('[data-testid="param-wallet"]');
    const innerHTML = await paramElement.innerHTML();

    // HTML should be escaped
    expect(innerHTML).not.toContain('<div');
    expect(innerHTML).toContain('&lt;div');
  });

  test('Event handlers in params should be escaped', async ({ page }) => {
    const eventPayload = '<img src=x onerror="alert(1)">';

    await page.setContent(`
      <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
        <input name="wallet" value='${eventPayload}'>
        <button type="submit">Submit</button>
      </form>
    `);

    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const paramElement = page.locator('[data-testid="param-wallet"]');
    const innerHTML = await paramElement.innerHTML();

    // img tag should be escaped
    expect(innerHTML).not.toContain('<img');
    expect(innerHTML).toContain('&lt;img');
  });

  test('Ampersand and special chars should be escaped', async ({ page }) => {
    const specialPayload = '< > & " \'';

    await page.setContent(`
      <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
        <input name="wallet" value="${specialPayload}">
        <button type="submit">Submit</button>
      </form>
    `);

    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const paramElement = page.locator('[data-testid="param-wallet"]');
    const innerHTML = await paramElement.innerHTML();

    // Special chars should be escaped
    expect(innerHTML).toContain('&lt;');
    expect(innerHTML).toContain('&gt;');
    expect(innerHTML).toContain('&amp;');
  });

  test('No JavaScript execution from malicious params', async ({ page }) => {
    // Set up a flag that would be set if XSS executes
    await page.addInitScript(() => {
      // @ts-ignore
      window.xssExecuted = false;
      // @ts-ignore
      window.alert = () => { window.xssExecuted = true; };
    });

    const xssPayload = '<script>window.xssExecuted=true</script>';

    await page.setContent(`
      <form method="POST" action="${BASE_URL}/${TEST_PAGE}">
        <input name="wallet" value="${xssPayload}">
        <button type="submit">Submit</button>
      </form>
    `);

    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    // Check that XSS did not execute
    const xssExecuted = await page.evaluate(() => {
      // @ts-ignore
      return window.xssExecuted;
    });

    expect(xssExecuted).toBe(false);
  });

});

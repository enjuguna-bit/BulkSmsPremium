// UPDATED WITH CORS & SIGNATURE HANDLING
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    const normalizePhone = (value) => {
      if (!value) return null;
      const digits = String(value).replace(/\D/g, "");
      if (digits.startsWith("0") && digits.length === 10) {
        return "254" + digits.substring(1);
      }
      if (digits.startsWith("254") && digits.length === 12) {
        return digits;
      }
      if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length === 9) {
        return "254" + digits;
      }
      return null;
    };

    const toNumber = (value) => {
      if (typeof value === "number") return value;
      if (typeof value === "string") {
        const parsed = parseFloat(value);
        return Number.isFinite(parsed) ? parsed : null;
      }
      return null;
    };

    const extractPayload = (body) => {
      if (body && typeof body === "object") {
        return body.data || body.payload || body;
      }
      return {};
    };

    const extractPhone = (payload, reference) => {
      const candidate =
        payload.phone ||
        payload.customer_phone ||
        payload.customer_contact ||
        payload.phone_number ||
        payload.msisdn ||
        payload.payer_phone ||
        payload.customer?.phone ||
        payload.customer?.phone_number ||
        payload.customer?.contact ||
        payload.meta?.phone ||
        payload.extra?.phone;

      const normalized = normalizePhone(candidate);
      if (normalized) return normalized;

      if (reference) {
        const match = String(reference).match(/(\+?254|0)?(7\d{8}|1\d{8})/);
        if (match) {
          return normalizePhone(match[0]);
        }
      }

      return null;
    };

    const isSuccessStatus = (status, event) => {
      const statusValue = status ? String(status).toLowerCase() : "";
      const eventValue = event ? String(event).toLowerCase() : "";
      const okStatuses = new Set([
        "success",
        "successful",
        "paid",
        "complete",
        "completed",
        "ok",
      ]);
      const okEvents = new Set([
        "payment.success",
        "payment.succeeded",
        "payment.completed",
        "charge.success",
        "collection.success",
      ]);
      return okStatuses.has(statusValue) || okEvents.has(eventValue);
    };

    // ========== CORS PREFLIGHT ==========
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-Lipana-Signature, X-Lipana-Event",
        },
      });
    }

    // ========== STATUS CHECK ==========
    if (request.method === "GET" && path === "/status") {
      const phone = url.searchParams.get("phone");
      const cleanPhone = normalizePhone(phone);
      if (!cleanPhone) {
        return new Response(JSON.stringify({
          error: "Invalid phone number",
        }, null, 2), {
          status: 400,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }

      // Get from KV
      const subData =
        (await env.SUBSCRIPTIONS.get(`sub:${cleanPhone}`, { type: "json" })) ||
        (await env.SUBSCRIPTIONS.get(`sub:+${cleanPhone}`, { type: "json" }));

      const response = {
        phone: cleanPhone,
        premium: false,
        message: "No active subscription",
      };

      if (subData) {
        response.premium = true;
        response.plan = subData.plan;
        response.amount = subData.amount;
        response.paid_until = subData.paid_until;
        response.last_payment = subData.last_payment_at;
        response.last_txn = subData.last_txn;
      }

      return new Response(JSON.stringify(response, null, 2), {
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // ========== HOME PAGE ==========
    if (request.method === "GET" && path === "/") {
      const html = `<!DOCTYPE html>
<html>
<head>
  <title>Payment System Active</title>
  <style>
    body { font-family: Arial; padding: 20px; max-width: 800px; margin: 0 auto; }
    .success { color: #28a745; font-weight: bold; }
    .test-btn { background: #28a745; color: white; padding: 12px 24px; border: none; border-radius: 5px; cursor: pointer; margin: 10px; font-size: 16px; }
    .test-btn:hover { background: #218838; }
    #result { margin-top: 20px; padding: 15px; background: #f8f9fa; border-radius: 5px; border: 1px solid #dee2e6; }
    pre { background: #343a40; color: #f8f9fa; padding: 10px; border-radius: 5px; overflow: auto; }
  </style>
</head>
<body>
  <h1>Payment System Active</h1>
  <p class="success">Lipa Na Dev M-PESA payments are LIVE!</p>
  <p>Webhook URL: <code>https://bulksmsbilling.enjuguna794.workers.dev/</code></p>

  <h3>Test Endpoints:</h3>
  <button class="test-btn" onclick="testStatus()">Test Status API</button>
  <button class="test-btn" onclick="testPayment()">Test Webhook</button>
  <button class="test-btn" onclick="viewLogs()">View Recent Logs</button>

  <div id="result"></div>

  <script>
    async function testStatus() {
      const result = document.getElementById("result");
      result.innerHTML = "Testing status for 254712345678...";

      try {
        const response = await fetch("/status?phone=254712345678");
        const data = await response.json();
        result.innerHTML = "<h4>Status Result:</h4><pre>" + JSON.stringify(data, null, 2) + "</pre>";
      } catch (error) {
        result.innerHTML = "Error: " + error.message;
      }
    }

    async function testPayment() {
      const result = document.getElementById("result");
      result.innerHTML = "Simulating test payment...";

      try {
        const response = await fetch("/", {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({
            transaction_id: "TEST_" + Date.now(),
            phone: "254712345678",
            amount: 200,
            status: "success",
            mpesa_receipt: "MPESA" + Math.random().toString(36).substr(2, 8).toUpperCase(),
            reference: "user_123_daily_" + Date.now(),
            timestamp: new Date().toISOString()
          })
        });

        const data = await response.json();
        result.innerHTML = "<h4>Payment Test Result:</h4><pre>" + JSON.stringify(data, null, 2) + "</pre>";

        // Check status after payment
        setTimeout(testStatus, 1000);
      } catch (error) {
        result.innerHTML = "Error: " + error.message;
      }
    }

    async function viewLogs() {
      const result = document.getElementById("result");
      result.innerHTML = "Checking recent logs...<br><em>Note: Logs appear in Cloudflare dashboard</em>";
    }
  </script>
</body>
</html>`;

      return new Response(html, { headers: { "Content-Type": "text/html" } });
    }

    // ========== LIPA NA DEV WEBHOOK HANDLER ==========
    if (request.method === "POST" && path === "/") {
      try {
        // Get raw body for signature verification
        const rawBody = await request.text();
        const signature = request.headers.get("x-lipana-signature");
        const event = request.headers.get("x-lipana-event");

        // Parse JSON
        const body = JSON.parse(rawBody);
        const payload = extractPayload(body);

        // Log everything for debugging
        console.log("Lipa Na Dev Webhook Received");
        console.log("Event:", event);
        console.log("Signature:", signature);
        console.log("Payment Data:", JSON.stringify(body, null, 2));

        // Extract data
        const txnId = payload.transaction_id || payload.id || "txn_" + Date.now();
        const reference = payload.reference || payload.custom_reference || payload.account || "";
        const cleanPhone = extractPhone(payload, reference);
        const amount = toNumber(payload.amount || payload.total_amount || payload.amount_received) ?? 200;
        const status = payload.status || payload.state || "unknown";
        const receipt = payload.mpesa_receipt || payload.receipt_number || payload.mpesaReceipt || "";

        console.log("Extracted:", { txnId, phone: cleanPhone, amount, status, receipt, reference });

        // Determine plan from amount
        const plan = amount >= 1000 ? "weekly" : "daily";

        // 1. Save to TRANSACTIONS KV
        const txnData = {
          id: txnId,
          phone: cleanPhone || null,
          amount,
          plan,
          status,
          mpesa_receipt: receipt,
          reference,
          event,
          signature,
          raw_body: rawBody.length > 500 ? rawBody.substring(0, 500) + "..." : rawBody,
          timestamp: new Date().toISOString(),
        };

        await env.TRANSACTIONS.put(`txn:${txnId}`, JSON.stringify(txnData));
        console.log("Transaction saved to KV:", txnId);

        // 2. Update SUBSCRIPTIONS if payment successful
        if (cleanPhone && isSuccessStatus(status, event)) {
          const expiry = new Date();
          expiry.setDate(expiry.getDate() + (plan === "weekly" ? 7 : 1));

          const subData = {
            phone: cleanPhone,
            status: "active",
            plan,
            amount,
            paid_until: expiry.toISOString(),
            last_txn: txnId,
            last_payment_at: new Date().toISOString(),
            mpesa_receipt: receipt,
            reference,
            updated_at: new Date().toISOString(),
          };

          await env.SUBSCRIPTIONS.put(`sub:${cleanPhone}`, JSON.stringify(subData));
          await env.SUBSCRIPTIONS.put(`sub:+${cleanPhone}`, JSON.stringify(subData));
          console.log("Subscription updated for:", cleanPhone);
          console.log("Plan:", plan, "Valid until:", expiry.toISOString());
        } else if (!cleanPhone) {
          console.warn("Missing phone in webhook payload; subscription not updated.");
        }

        // 3. Log to LOGS KV
        await env.LOGS.put(`log:${Date.now()}:${txnId}`, JSON.stringify({
          type: "webhook",
          event,
          phone: cleanPhone,
          amount,
          status,
          txnId,
          timestamp: new Date().toISOString(),
        }));

        // Return success to Lipa Na Dev
        return new Response(JSON.stringify({
          success: true,
          message: "Payment processed successfully",
          transaction_id: txnId,
          phone: cleanPhone,
          amount,
          status,
          processed_at: new Date().toISOString(),
        }, null, 2), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });

      } catch (error) {
        console.error("Webhook processing error:", error);

        return new Response(JSON.stringify({
          success: false,
          error: "Processing error",
          message: error.message,
        }, null, 2), {
          status: 500,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
    }

    // ========== DEFAULT / NOT FOUND ==========
    return new Response(JSON.stringify({
      message: "Lipa Na Dev Payment System",
      endpoints: {
        "GET /": "Info dashboard",
        "GET /status?phone=254...": "Check subscription status",
        "POST /": "Lipa Na Dev webhook endpoint",
      },
      status: "active",
      timestamp: new Date().toISOString(),
    }, null, 2), {
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
    });
  }
};

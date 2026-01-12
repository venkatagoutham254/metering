# 🎯 Automatic Invoice Generation - Implementation Complete

## Overview
Automatic invoice generation has been implemented in the **metering** microservice. The system now automatically detects when subscription billing periods end and generates invoices without any manual intervention.

---

## ✅ What Was Implemented

### **1. BillingPeriodMonitorService.java** (NEW)
**Location**: `src/main/java/aforo/metering/service/BillingPeriodMonitorService.java`

**Purpose**: Scheduled service that monitors billing periods and generates invoices automatically.

**Schedule**: Runs every 10 minutes (`@Scheduled(cron = "0 */10 * * * *")`)
- Runs at: 00:00, 00:10, 00:20, 00:30, 00:40, 00:50, 01:00, 01:10, etc.
- Ensures hourly billing cycles are caught very promptly (within 10 minutes)

**Flow**:
```
Every 10 minutes:
1. Get all organization IDs from ingestion_event table
2. For each organization:
   a. Generate service JWT token
   b. Fetch all active subscriptions
   c. Check if billing period ended (now >= currentBillingPeriodEnd)
   d. Check if invoice already exists (prevent duplicates)
   e. If period ended + no invoice exists:
      - Calculate metering for the period
      - Create invoice
      - QuickBooks webhook fires automatically
3. Log results and continue to next organization
```

**Key Features**:
- ✅ Multi-tenant safe (processes all organizations independently)
- ✅ Duplicate prevention (checks if invoice exists before creating)
- ✅ Error isolation (one org/subscription failure doesn't affect others)
- ✅ Automatic QuickBooks sync (webhook fires from InvoiceService)
- ✅ Comprehensive logging with emojis for easy monitoring

---

### **2. JwtTokenGenerator.java** (NEW)
**Location**: `src/main/java/aforo/metering/util/JwtTokenGenerator.java`

**Purpose**: Generates service-level JWT tokens for scheduled jobs to authenticate with external services.

**Why Needed**: 
- Scheduled jobs have no user context
- SubscriptionClient requires JWT token for authentication
- Service tokens include organization ID in claims

**Token Details**:
- Subject: "metering-service"
- Validity: 2 hours
- Claims: `organizationId`, `orgId`, `type: service`

---

### **3. SubscriptionClient Enhancement** (MODIFIED)
**Location**: `src/main/java/aforo/metering/client/SubscriptionClient.java`

**New Method**: `getAllActiveSubscriptions(Long organizationId, String jwtToken)`

**Purpose**: Fetch all active subscriptions for an organization.

**API Call**:
```
GET /api/subscriptions?organizationId={orgId}&status=ACTIVE
Headers:
  - Authorization: Bearer {serviceToken}
  - X-Organization-Id: {orgId}
```

**Returns**: List of `SubscriptionResponse` objects with billing period information

---

### **4. OrganizationRepository.java** (NEW)
**Location**: `src/main/java/aforo/metering/repository/OrganizationRepository.java`

**Purpose**: Fetch all organization IDs that have activity in the system.

**Query**: 
```sql
SELECT DISTINCT organization_id 
FROM ingestion_event 
WHERE organization_id IS NOT NULL 
ORDER BY organization_id
```

**Why Needed**: Scheduled jobs need to know which organizations to process.

---

### **5. pom.xml Dependency** (MODIFIED)
**Added**: `nimbus-jose-jwt` (version 9.37.3)

**Purpose**: JWT token generation library for service tokens.

---

## 🔄 Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│           AUTOMATIC INVOICE GENERATION FLOW                      │
└─────────────────────────────────────────────────────────────────┘

⏰ EVERY 10 MINUTES (Scheduled)
   ↓
┌──────────────────────────────────────────────────────────────┐
│ BillingPeriodMonitorService.monitorBillingPeriodsAndGenerate│
└──────────────────────────────────────────────────────────────┘
   ↓
📊 Get Organization IDs (OrganizationRepository)
   ↓
   For Each Organization:
   ├─ 🔑 Generate Service JWT Token (JwtTokenGenerator)
   ├─ 📡 Fetch Active Subscriptions (SubscriptionClient)
   │    └─ GET /api/subscriptions?status=ACTIVE
   ↓
   For Each Subscription:
   ├─ 📅 Parse currentBillingPeriodEnd
   ├─ ⏱️ Check: now >= periodEnd?
   ├─ 🔍 Check: Invoice exists for period?
   ↓
   If Period Ended + No Invoice:
   ├─ 📊 Calculate Metering (MeterService.estimate)
   │    └─ Count events from ingestion_event
   │    └─ Apply pricing models (FlatFee, Usage, Tiered, etc.)
   ├─ 💰 Create Invoice (InvoiceService.createInvoiceFromMeterResponse)
   │    └─ Generate invoice number
   │    └─ Create invoice + line items
   │    └─ Save to invoice table
   │    └─ Publish InvoiceCreatedEvent
   │    └─ 📤 Call QuickBooksWebhookClient.notifyInvoiceCreated()
   ↓
   QuickBooks Integration (Automatic):
   ├─ 📨 Receive webhook at /api/quickbooks/webhook/invoice-created
   ├─ 📡 Fetch full invoice from metering service
   ├─ 🔄 Map customer and service items
   └─ 💳 Create invoice in QuickBooks
```

---

## 🛡️ Safety Features

### **Duplicate Prevention**
```java
invoiceRepository.existsForPeriod(orgId, subscriptionId, periodStart, periodEnd)
```
- Checks if invoice already exists for exact period
- Prevents duplicate invoices if job runs multiple times

### **Error Isolation**
- Organization-level try-catch: One org failure doesn't affect others
- Subscription-level try-catch: One subscription failure doesn't affect others
- Comprehensive error logging for debugging

### **Tenant Context Management**
```java
try {
    TenantContext.setOrganizationId(orgId);
    TenantContext.setJwtToken(serviceToken);
    // Process organization
} finally {
    TenantContext.clear(); // Always cleanup
}
```

### **Date Parsing**
- Format: "26 Dec, 2025 11:07 IST"
- Handles timezone conversions properly
- Falls back gracefully on parse errors

---

## 📊 Expected Behavior

### **Scenario 1: Hourly Billing Subscription**
- Subscription created: `Jan 3, 2026 14:00 IST`
Subscription: Created Jan 3, 2026 14:00
Period: 14:00 → 15:00
Monitor runs: 15:00, 15:10, or 15:20 ✅ (within 10 minutes)
Result: Invoice auto-generated!
``` within 10 minutes of period end

### **Scenario 2: Monthly Billing Subscription**
- Subscription created: `Jan 1, 2026 00:00 IST`
- Billing period: `Jan 1` to `Feb 1`
- Monitor runs at: `Feb 1, 00:00` ✅
- **Result**: Invoice auto-generated on Feb 1

### **Scenario 3: Already Invoiced**
- Invoice exists for period
- Monitor runs again
- **Result**: Skipped (duplicate prevention)

### **Scenario 4: No Active Subscriptions**
- Organization has no ACTIVE subscriptions
- **Result**: Skipped (logs "No active subscriptions")

---

## 🧪 Testing Instructions

### **Step 1: Create Test Subscription**
```bash
# Create subscription with short billing period (1 hour for quick testing)
POST /api/subscriptions
{
  "customerId": 1,
  "productId": 1,
  "ratePlanId": 1,
  "status": "ACTIVE",
  "billingFrequency": "HOURLY"
}
```

### **Step 2: Ingest Some Events**
```bash
# Upload events for the subscription
POST /api/ingestion/files
- Upload CSV/JSON with subscription events
```

### **Step 3: Wait for Billing Period to End**
- If hourly: Wait 1 hour
- Or manually trigger (for testing):
  - Set `currentBillingPeriodEnd` to past time in subscription DB

### **Step 4: Monitor Logs**
Watch metering service logs for:
```
🔍 Starting billing period monitoring job...
📊 Processing organization 1...
🔔 Subscription X billing period has ENDED!
✅ Should generate invoice for subscription X
💰 Generating invoice for subscription X
📊 Metering calculated: 50 events, total amount: $210.00
🎉 Invoice INV-XXX created automatically
✅ Billing period monitoring completed. Generated 1 invoice(s)
```

### **Step 5: Verify Invoice Created**
```bash
# Check metering database
SELECT * FROM invoice WHERE subscription_id = X;

# Check invoice API
GET /api/invoices/subscription/{subscriptionId}
```

### **Step 6: Verify QuickBooks Sync**
- Check QuickBooks integration logs
- Verify invoice appears in QuickBooks sandbox

---

## 🔧 Configuration

### **Schedule Configuration** (if you need to change)
In `BillingPeriodMonitorService.java`:
```java
@Scheduled(cron = "0 */10 * * * *")  // Every 10 minutes
```

**Cron Expression Options**:
- Every 5 minutes: `"0 */5 * * * *"`
- Every 10 minutes: `"0 */10 * * * *"` ✅ (Current)
- Every 15 minutes: `"0 */15 * * * *"`
- Every 30 minutes: `"0 */30 * * * *"`
- Every hour: `"0 0 * * * *"`
- Daily at 1 AM: `"0 0 1 * * *"`

### **Application Properties**
Already configured in `application.yml`:
```yaml
aforo:
  subscription-service:
    base-url: http://host.docker.internal:8084
  jwt:
    secret: change-me-please-change-me-32-bytes-min
    issuer: aforo-metering
```

---

## 📝 Logging Guide

### **Success Logs**
```
🔍 Starting billing period monitoring job...
📊 Processing organization 1...
Found 3 active subscription(s) for organization 1
🔔 Subscription 123 billing period has ENDED!
💰 Generating invoice for subscription 123
📊 Metering calculated: 100 events, total amount: $500.00
🎉 Invoice INV-1-9-20260103 created automatically
✅ Generated 1 invoice(s) for organization 1
✅ Billing period monitoring completed. Generated 1 invoice(s) across all organizations.
```

### **Skip Logs (Normal)**
```
Subscription 456 billing period has not ended yet. End: 2026-01-03T16:00:00Z, Now: 2026-01-03T15:30:00Z
Invoice already exists for subscription 789 period ... Skipping.
No active subscriptions for organization 2
```

### **Error Logs**
```
❌ Error processing organization 5: Connection timeout
❌ Failed to generate invoice for subscription 999: Customer not found
❌ Fatal error in billing period monitoring: Database connection failed
```

---

## 🚨 Important Notes

### **1. Subscription Service Must Return Billing Period**
The Subscription Service must include these fields in `SubscriptionResponse`:
- `currentBillingPeriodStart`
- `currentBillingPeriodEnd`
- Format: `"dd MMM, yyyy HH:mm z"` (e.g., "26 Dec, 2025 11:07 IST")

### **2. QuickBooks Connection Required**
- Organization must have active QuickBooks connection
- If not connected, invoice created but not synced to QuickBooks
- Logs will show: "No active QuickBooks connection for organization X. Skipping sync."

### **3. Customer Must Be Synced First**
- Customer must exist in QuickBooks before invoice creation
- Sync customers first via: `POST /api/quickbooks/sync/customer`

### **4. Service Token Generation**
- Uses metering service's JWT secret from `application.yml`
- Tokens valid for 2 hours
- Automatically includes organization ID in claims

---

## ✅ What Happens Automatically Now

1. ✅ **Every 10 minutes**: Monitor runs
2. ✅ **Detects ended periods**: Checks all subscriptions
3. ✅ **Calculates usage**: Meters the billing period
4. ✅ **Creates invoice**: Saves to database
5. ✅ **Syncs to QuickBooks**: Webhook fires automatically
6. ✅ **Prevents duplicates**: Skips if invoice exists

---

## 🎉 Summary

**NO MANUAL INTERVENTION REQUIRED!**

Once a subscription's billing period ends:
1. System automatically detects it within 10 minutes
2. Calculates usage and cost
3. Creates invoice in metering DB
4. Syncs to QuickBooks automatically

**The entire flow is now fully automatic!**

---

## 📞 Support

If issues occur:
1. Check metering service logs for errors
2. Verify subscription has billing period fields
3. Ensure QuickBooks is connected
4. Check customer is synced to QuickBooks
5. Verify ingestion events exist for the subscription

**All logs use emojis (🔍💰🎉❌) for easy filtering!**

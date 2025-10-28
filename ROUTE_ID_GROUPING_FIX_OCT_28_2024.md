# Route ID Grouping Fix - October 28, 2024

## Problem Identified

Bus stop cards were showing inconsistent numbers of arrival times across different routes:
- Some routes showed only 1 arrival time (live only)
- Some routes showed 2 arrival times (live + 1 static)
- Some routes showed 3 arrival times (live + 2 static) ✓ Expected behavior

## Root Cause

The issue was a **route ID vs short name mismatch** in the arrival grouping logic:

### How It Was Before:
1. **Repository** (`getMergedArrivalsForStop`): Grouped arrivals by **full GTFS route ID** (e.g., "107890-20240901")
2. Limited to 1 real-time + 2 static arrivals **per full route ID**
3. **ViewModel**: Converted full route IDs to short names (e.g., "1C") **AFTER** limiting

### The Problem:
Multiple full route IDs can map to the same short name:
- "107890-20240901" → "1C" (weekday service)
- "107890-20241001" → "1C" (weekend service)
- "107891-20240901" → "1C" (different direction or variant)

When filtering by short name (e.g., "1C") in the MapScreen, you might get:
- 1 RT + 2 static from "107890-20240901" = 3 arrivals ✓
- OR just 1 RT from "107890-20240901" if there were no static times for that specific variant
- OR 1 RT + 1 static from "107891-20240901" = 2 arrivals

The grouping was happening at the wrong level!

## Solution Implemented

### Changes Made:

#### 1. TransitRepository.kt
- **Updated method signature**: Added `routeIdToShortName: Map<String, String>` parameter
- **Fixed grouping logic**: 
  - Convert route IDs to short names **BEFORE** grouping
  - Group all arrivals by **short name** instead of full route ID
  - **Sort arrivals by time** before taking the first 1 RT + 2 static
  - This ensures all route variants (107890-X, 107891-X) that map to "1C" are grouped together

#### 2. TransitViewModel.kt
- Updated both calls to `getMergedArrivalsForStop()` to pass the `routeIdToShortName` mapping:
  - In `loadFavoritesWithArrivals()` 
  - In `getMergedArrivalsForStop()`

## Expected Behavior Now

For every route (by short name):
- **1 real-time arrival** (soonest live prediction across all route variants)
- **2 static arrivals** (next 2 scheduled times across all route variants)

This will be consistent across:
- Home Screen (favorite stops)
- Map Screen (bus stop cards)
- Nearby Stops Screen

## Additional Improvements

- Arrivals are now **sorted by time** before limiting, ensuring users always see the **soonest** arrivals
- Debug logging improved to show route short names instead of cryptic full IDs

## Files Modified

1. `/app/src/main/java/com/kiz/transitapp/data/repository/TransitRepository.kt`
   - Updated `getMergedArrivalsForStop()` method
   - Added short name conversion and proper grouping logic

2. `/app/src/main/java/com/kiz/transitapp/ui/viewmodel/TransitViewModel.kt`
   - Updated calls to pass route mapping to repository

## Testing Recommendations

When buses are running, verify:
1. All routes show consistent arrival counts (1 live + 2 static when available)
2. The arrivals shown are the **soonest** upcoming times
3. Route IDs match correctly between the timetable and the bus stop cards
4. Filtering by selected route in MapScreen works correctly


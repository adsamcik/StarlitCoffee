# Research: Coffee Bloom Phase Duration & Dose Scaling

## Context
Building a guided brew timer for the NextLevel Pulsar (and other pour-over methods). The app currently uses a fixed 45-second bloom duration regardless of coffee dose. Need to determine whether this is correct or if bloom time should scale with dose.

## Questions to Answer

1. **Standard bloom duration**: What is the widely accepted bloom phase duration for pour-over coffee? Is 30–45 seconds the consensus, or do experts recommend longer/shorter?

2. **Dose scaling**: Should bloom duration increase with higher coffee doses (e.g., 30g vs 15g)? Does a larger bed of grounds need more time to fully degas?

3. **Roast level factor**: Does roast level (light vs dark) affect optimal bloom time? Dark roasts have more CO₂ — does that mean longer bloom?

4. **Freshness factor**: Does bean freshness (days off roast) affect bloom duration?

5. **Immersion/valve brewers**: For devices like the Pulsar where the valve is closed during bloom (full immersion bloom), does the optimal bloom time differ from open-flow pour-over blooms?

6. **Bloom water amount**: Current app uses coffee × 3 (bloom multiplier). Is 2× or 3× more standard? Does this interact with bloom duration?

## What I Need Back
- A recommended bloom duration formula or rule (fixed time, or scaled by dose)
- Whether the current 45s fixed duration is reasonable or should change
- Any credible sources (Scott Rao, James Hoffmann, SCA, Barista Hustle, research papers)
- If dose-scaling is recommended: what formula (e.g., base + seconds per gram)

## Current App Values (for reference)
- Pulsar bloom: 45s (15s saturate + 30s steep), bloom water = coffee × 3
- V60 bloom: 45s, bloom water = coffee × 2.5
- Dose range: 15–30g (optimal 20–25g)

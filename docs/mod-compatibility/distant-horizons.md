# Globe World + Distant Horizons

Distant Horizons should work with Globe World for normal terrain, but it is not
currently optimized for Globe World's wrapped coordinate model.

## Recommended Settings

- Use DH on medium or large Globe World tiles
- Disable DH for very small worlds, curvature makes it irrelevant
- The Globe World UI only shows its DH Earth-curvature advice when the computed
  ratio is within DH's supported `50..5000` range.
- Do not use Globe World curvature and DH together. DH has its own curvature
  renderer and Globe World's vanilla shader curvature does not bend DH LOD
  terrain.

## Expected Caveats

- DH may cache the same wrapped terrain under multiple alias coordinates.
- Canonical chunk updates may not invalidate every alias LOD copy.

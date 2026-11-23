-- Run in Perfetto UI against the captured trace. These are actual compositor
-- timelines, not predicted Choreographer timestamps. Do not treat the next frame
-- after a submit marker as proof that a particular TextureView buffer was shown.
SELECT a.ts, a.dur, a.surface_frame_token, a.display_frame_token,
       a.present_type, a.jank_type, a.layer_name, p.name
FROM actual_frame_timeline_slice a
LEFT JOIN process p USING (upid)
WHERE p.name = 'com.example.facefit' OR p.name = '/system/bin/surfaceflinger'
ORDER BY a.ts;

SELECT ts, dur, name FROM slice
WHERE name GLOB 'FaceFitVTO.submit.*' OR name = 'FaceFitVTO.gpuPending'
ORDER BY ts;

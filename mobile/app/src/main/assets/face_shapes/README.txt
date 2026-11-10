FaceFit Results illustrations

Put your prepared pictures directly in this folder:
D:/AndroidStudioProjects/FaceFit-AI/mobile/app/src/main/assets/face_shapes

Your current filenames are supported directly:
  face_shape_oval.png    -> Oval result
  face_shape_round.png   -> Round result
  face_shape_square.png  -> Square result
  face_shape_oblong.png  -> Long / oblong result
  face_shape_heart.png   -> Heart illustration, ready if the classifier supports Heart later

An optional face_shape_unclear.png can illustrate an Unclear result. Simple names such as oval.png and long.png remain supported. The prefixed filenames are preferred when both versions exist.

PNG, JPG, JPEG and WebP are supported. The current classifier estimates Oval, Round, Square, Long or Unclear. Adding an image does not add a new detection category.

Use a neatly cropped illustration of the face shape. The app shows the original picture by itself, with no extra FACE SHAPE heading or name underneath. Text already included inside your PNG remains part of that picture. The full picture keeps its proportions inside the rounded card. Keep each image reasonably small (about 400-600 pixels on the longest edge).

After adding or replacing images, rebuild and Run the Android app so the files are packaged into the app. A simple built-in illustration appears until a matching picture is available.

These pictures are display illustrations only. They are not used to detect face shape or train a model. The detected shape chooses which picture to show.

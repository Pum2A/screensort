"""Decode bounded PNG/JPEG bytes without reading or writing source paths."""

from contextlib import closing
from io import BytesIO

from PIL import Image

MAX_IMAGE_BYTES = 50 * 1024 * 1024
MAX_IMAGE_PIXELS = 40_000_000
MAX_IMAGE_EDGE = 20_000


def _validate_dimensions(width: int, height: int) -> None:
    if width > MAX_IMAGE_EDGE or height > MAX_IMAGE_EDGE:
        raise ValueError("Image dimensions are too large.")

    if width * height > MAX_IMAGE_PIXELS:
        raise ValueError("Image exceeds the pixel limit.")


def validate_prepared_image(image: Image.Image) -> None:
    """Check the in-memory contract without opening or decoding another file."""
    if image.mode != "RGB":
        raise ValueError("Expected a prepared RGB image.")

    _validate_dimensions(*image.size)


def prepare_image(image_bytes: bytes) -> Image.Image:
    """Return an independent RGB image; the caller must close it after use."""
    if not image_bytes:
        raise ValueError("Image must not be empty.")

    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise ValueError("Image exceeds the 50 MiB limit.")

    with BytesIO(image_bytes) as source:
        with closing(Image.open(source, formats=("PNG", "JPEG"))) as image:
            _validate_dimensions(*image.size)
            image.load()

            # Opaque RGB needs no alpha channel or background image.
            if image.mode == "RGB" and "transparency" not in image.info:
                return image.convert("RGB")

            with closing(image.convert("RGBA")) as rgba:
                rgb = Image.new("RGB", image.size, "white")
                try:
                    with closing(rgba.getchannel("A")) as alpha:
                        rgb.paste(rgba, mask=alpha)
                except BaseException:
                    # Release an unfinished image, then propagate the error.
                    rgb.close()
                    raise

                return rgb

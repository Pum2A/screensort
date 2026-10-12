"""Preparation checks use generated images, never user files or ML models."""

from contextlib import closing
from io import BytesIO
from unittest.mock import Mock

import pytest
from PIL import Image, UnidentifiedImageError

from screensort_analysis.image import preparation
from screensort_analysis.image.preparation import (
    prepare_image,
    validate_prepared_image,
)


def image_bytes(mode="RGB", image_format="PNG"):
    with closing(Image.new(mode, (4, 3))) as image, BytesIO() as output:
        image.save(output, format=image_format)
        return output.getvalue()


@pytest.mark.parametrize(
    ("mode", "image_format"),
    [("RGB", "PNG"), ("RGBA", "PNG"), ("L", "PNG"), ("P", "PNG"), ("RGB", "JPEG")],
)
def test_preparation_returns_an_independent_decoded_rgb_image(mode, image_format):
    with closing(prepare_image(image_bytes(mode, image_format))) as prepared:
        assert prepared.mode == "RGB"
        assert prepared.size == (4, 3)
        assert prepared.getpixel((0, 0)) is not None


def test_transparent_pixels_are_composited_on_white_without_losing_opaque_color():
    with closing(Image.new("RGBA", (3, 1))) as image, BytesIO() as output:
        image.putdata([(200, 0, 0, 0), (20, 40, 60, 255), (0, 0, 0, 128)])
        image.save(output, format="PNG")
        data = output.getvalue()

    with closing(prepare_image(data)) as prepared:
        assert prepared.getpixel((0, 0)) == (255, 255, 255)
        assert prepared.getpixel((1, 0)) == (20, 40, 60)
        assert prepared.getpixel((2, 0)) == (127, 127, 127)


def test_rgb_pixels_remain_identical_to_the_previous_white_background_pipeline():
    with closing(Image.new("RGB", (1, 1), (20, 40, 60))) as image, BytesIO() as output:
        image.save(output, format="PNG")
        data = output.getvalue()

    with closing(prepare_image(data)) as prepared:
        assert prepared.getpixel((0, 0)) == (20, 40, 60)


def test_rgb_png_with_a_transparent_color_still_gets_a_white_background():
    with closing(Image.new("RGB", (2, 1), (20, 40, 60))) as image, BytesIO() as output:
        image.putpixel((1, 0), (60, 40, 20))
        image.save(output, format="PNG", transparency=(20, 40, 60))
        data = output.getvalue()

    with closing(prepare_image(data)) as prepared:
        assert prepared.getpixel((0, 0)) == (255, 255, 255)
        assert prepared.getpixel((1, 0)) == (60, 40, 20)


@pytest.mark.parametrize("data", [b"", b"not an image", b"\x89PNG\r\n\x1a\n"])
def test_empty_fake_and_truncated_input_is_rejected(data):
    with pytest.raises((ValueError, OSError)):
        prepare_image(data)


@pytest.mark.parametrize("image_format", ["GIF", "BMP", "TIFF"])
def test_only_actual_png_and_jpeg_formats_are_allowed(image_format):
    with pytest.raises(UnidentifiedImageError):
        prepare_image(image_bytes(image_format=image_format))


def test_byte_limit_is_checked_before_image_open(monkeypatch):
    monkeypatch.setattr(preparation, "MAX_IMAGE_BYTES", 7)
    open_image = Mock()
    monkeypatch.setattr(preparation.Image, "open", open_image)

    with pytest.raises(ValueError, match="50 MiB"):
        prepare_image(b"12345678")

    open_image.assert_not_called()


@pytest.mark.parametrize("dimensions", [(20_001, 1), (1, 20_001), (8_000, 5_001)])
def test_dimension_limits_are_checked_before_pixel_decoding(monkeypatch, dimensions):
    source = Mock(spec=Image.Image)
    source.size = dimensions
    monkeypatch.setattr(preparation.Image, "open", Mock(return_value=source))

    with pytest.raises(ValueError, match="dimensions|pixel limit"):
        prepare_image(b"header-only fixture")

    source.load.assert_not_called()
    source.close.assert_called_once_with()


def test_unfinished_rgb_image_is_closed_if_compositing_fails(monkeypatch):
    data = image_bytes(mode="RGBA")
    prepared = Image.new("RGB", (4, 3), "white")
    close_image = Mock(wraps=prepared.close)
    monkeypatch.setattr(prepared, "close", close_image)
    monkeypatch.setattr(
        prepared, "paste", Mock(side_effect=RuntimeError("paste failed"))
    )
    monkeypatch.setattr(preparation.Image, "new", Mock(return_value=prepared))

    with pytest.raises(RuntimeError, match="paste failed"):
        prepare_image(data)

    close_image.assert_called_once_with()


def test_prepared_contract_rejects_non_rgb_images():
    with closing(Image.new("RGBA", (1, 1))) as image:
        with pytest.raises(ValueError, match="RGB"):
            validate_prepared_image(image)


def test_prepared_contract_does_not_take_ownership_of_the_image():
    with closing(Image.new("RGB", (1, 1), "white")) as image:
        validate_prepared_image(image)
        assert image.getpixel((0, 0)) == (255, 255, 255)

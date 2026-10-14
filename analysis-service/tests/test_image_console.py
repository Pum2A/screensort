"""Developer console reads only selected fixtures and never changes them."""

import pytest

from screensort_analysis.image import console


def test_read_image_bytes_leaves_the_selected_file_unchanged(tmp_path):
    source = tmp_path / "sample.png"
    source.write_bytes(b"selected file bytes")
    before = source.stat()

    assert console.read_image_bytes(source) == b"selected file bytes"

    after = source.stat()
    assert after.st_size == before.st_size
    assert after.st_mtime_ns == before.st_mtime_ns


def test_read_image_bytes_rejects_empty_input(tmp_path):
    source = tmp_path / "empty.png"
    source.touch()

    with pytest.raises(ValueError, match="empty"):
        console.read_image_bytes(source)


def test_read_image_bytes_rejects_oversized_input_with_a_small_test_limit(
    monkeypatch, tmp_path
):
    source = tmp_path / "large.png"
    source.write_bytes(b"12345")
    monkeypatch.setattr(console, "MAX_IMAGE_BYTES", 4)

    with pytest.raises(ValueError, match="50 MiB"):
        console.read_image_bytes(source)


@pytest.mark.parametrize("kind", ["directory", "missing"])
def test_read_image_bytes_rejects_non_files(tmp_path, kind):
    path = tmp_path if kind == "directory" else tmp_path / "missing.png"

    with pytest.raises(ValueError, match="regular"):
        console.read_image_bytes(path)

import math


def area_of_circle(radius: float) -> float:
    """Return the area of a circle given its radius."""
    if radius < 0:
        raise ValueError("Radius cannot be negative")
    return math.pi * radius ** 2


if __name__ == "__main__":
    radius = float(input("Enter the radius of the circle: "))
    area = area_of_circle(radius)
    print(f"Area of the circle: {area:.2f}")

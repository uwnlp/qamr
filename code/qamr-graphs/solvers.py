from itertools import product
import logging
import numpy

def discrete_brute_minimizer(possible_vals, func):
    """
    Find an assignment from possible vals (list of lists of possible values)
    which minimizes the func function - should take a list of arbitrary length of arguments.
    Uses brute force to find the optimal assignment. Expontential in the number of possible assignments.
    exclusive - the options are mutually exclusive, e.g., without repetition
    partial - Allows for partial solutions, hopefully will happen only in cases where there's no full alignment.
    """
    return min([(assignment, func(assignment))
                for assignment in product(*possible_vals)
                # Include only unique assignments
                if (len(set(filter(lambda x: x >= 0, assignment))) == len(filter(lambda x: x >= 0, assignment)))
    ],
               key = lambda (_, score): score)

def mean_distance_from_centroid(cluster):
    """
    Given a cluster of *POSITIVE* coordinates, calculate the mean absolute distance
    of all of the points from its centroid.
    Negative coordinates are considered to be mapped to NONE
    """
    pos_cluster = filter(lambda x: x >= 0, cluster)

    # Calculate centroid based only on positive points
    centroid = (sum(pos_cluster)*1.0) / len(pos_cluster) if pos_cluster \
               else 0

    # Calculate the mean distance of *all* coordinates (including negative) from centroid
    return numpy.mean([numpy.abs(p - centroid) for p in cluster])


if __name__ == "__main__":
    ls = [[1, -100], [5], [6], [-4]]
    ass = discrete_brute_minimizer(ls, mean_distance_from_centroid)

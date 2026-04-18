"""
Analyze Raw Pose Data from TT Coach AI
Extract and visualize pose landmarks
"""

import json
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path

def load_raw_poses(filepath):
    """Load raw_poses.jsonl file"""
    poses = []
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip():
                poses.append(json.loads(line))
    return poses

def extract_landmark_trajectory(poses, landmark_index):
    """Extract x,y,z trajectory for specific landmark"""
    trajectory = []
    for pose in poses:
        lm = pose['landmarks'][landmark_index]
        trajectory.append((lm['x'], lm['y'], lm['z'], lm['visibility']))
    return np.array(trajectory)

def calculate_velocity(trajectory, fps=30):
    """Calculate velocity from trajectory"""
    dt = 1.0 / fps
    velocities = []
    
    for i in range(len(trajectory) - 1):
        dx = trajectory[i+1][0] - trajectory[i][0]
        dy = trajectory[i+1][1] - trajectory[i][1]
        dz = trajectory[i+1][2] - trajectory[i][2]
        
        velocity = np.sqrt(dx**2 + dy**2 + dz**2) / dt
        velocities.append(velocity)
    
    return np.array(velocities)

def plot_skeleton(pose, title="Pose Skeleton"):
    """Plot 2D skeleton from pose data"""
    landmarks = pose['landmarks']
    
    # Extract x, y coordinates
    x = [lm['x'] for lm in landmarks]
    y = [lm['y'] for lm in landmarks]
    
    # MediaPipe pose connections
    connections = [
        # Face
        (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
        # Body
        (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
        (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
        (11, 23), (12, 24), (23, 24),
        # Legs
        (23, 25), (25, 27), (27, 29), (29, 31), (27, 31),
        (24, 26), (26, 28), (28, 30), (30, 32), (28, 32)
    ]
    
    plt.figure(figsize=(8, 10))
    
    # Draw connections
    for start, end in connections:
        plt.plot([x[start], x[end]], [y[start], y[end]], 'b-', linewidth=2, alpha=0.6)
    
    # Draw keypoints
    plt.scatter(x, y, c='red', s=50, zorder=3)
    
    # Annotate important keypoints
    important_keypoints = {
        11: 'L_SHOULDER',
        12: 'R_SHOULDER',
        13: 'L_ELBOW',
        14: 'R_ELBOW',
        15: 'L_WRIST',
        16: 'R_WRIST',
        23: 'L_HIP',
        24: 'R_HIP'
    }
    
    for idx, name in important_keypoints.items():
        plt.annotate(name, (x[idx], y[idx]), fontsize=8, 
                    xytext=(5, 5), textcoords='offset points')
    
    plt.gca().invert_yaxis()  # Invert Y axis (image coordinates)
    plt.title(title)
    plt.xlabel('X (normalized)')
    plt.ylabel('Y (normalized)')
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.show()

def analyze_stroke_motion(poses, landmark_index=16):
    """Analyze motion of specific landmark (default: right wrist)"""
    trajectory = extract_landmark_trajectory(poses, landmark_index)
    velocities = calculate_velocity(trajectory)
    
    print(f"=== Stroke Motion Analysis (Landmark {landmark_index}) ===")
    print(f"Total frames: {len(poses)}")
    print(f"Duration: {len(poses) / 30:.2f} seconds")
    print(f"\nTrajectory:")
    print(f"  Start: x={trajectory[0][0]:.3f}, y={trajectory[0][1]:.3f}, z={trajectory[0][2]:.3f}")
    print(f"  End:   x={trajectory[-1][0]:.3f}, y={trajectory[-1][1]:.3f}, z={trajectory[-1][2]:.3f}")
    print(f"\nVelocity:")
    print(f"  Max: {np.max(velocities):.2f} units/sec")
    print(f"  Avg: {np.mean(velocities):.2f} units/sec")
    print(f"  Min: {np.min(velocities):.2f} units/sec")
    print(f"\nVisibility:")
    print(f"  Avg: {np.mean(trajectory[:, 3]):.2f}")
    print(f"  Min: {np.min(trajectory[:, 3]):.2f}")
    
    return trajectory, velocities

def plot_wrist_trajectory_3d(poses):
    """Plot 3D wrist trajectory"""
    right_wrist = extract_landmark_trajectory(poses, 16)
    
    fig = plt.figure(figsize=(10, 8))
    ax = fig.add_subplot(111, projection='3d')
    
    ax.plot(right_wrist[:, 0], right_wrist[:, 1], right_wrist[:, 2], 
            'r-', linewidth=2, label='Right Wrist')
    ax.scatter(right_wrist[0, 0], right_wrist[0, 1], right_wrist[0, 2], 
              c='green', s=100, label='Start')
    ax.scatter(right_wrist[-1, 0], right_wrist[-1, 1], right_wrist[-1, 2], 
              c='red', s=100, label='End')
    
    ax.set_xlabel('X (normalized)')
    ax.set_ylabel('Y (normalized)')
    ax.set_zlabel('Z (depth, meters)')
    ax.set_title('Right Wrist 3D Trajectory')
    ax.legend()
    plt.show()

def compare_landmarks(poses, landmark_indices, labels):
    """Compare trajectories of multiple landmarks"""
    plt.figure(figsize=(12, 8))
    
    for idx, label in zip(landmark_indices, labels):
        trajectory = extract_landmark_trajectory(poses, idx)
        plt.plot(trajectory[:, 0], trajectory[:, 1], label=label, marker='o', markersize=3)
    
    plt.gca().invert_yaxis()
    plt.xlabel('X (normalized)')
    plt.ylabel('Y (normalized)')
    plt.title('Landmark Trajectories Comparison')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.show()

def main():
    # Load raw poses
    poses_file = Path('logs_export/raw_poses.jsonl')
    
    if not poses_file.exists():
        print(f"Error: {poses_file} not found!")
        print("Make sure you:")
        print("1. Enabled raw pose logging in PoseAnalysisProcessor.kt")
        print("2. Ran training with camera")
        print("3. Exported logs using .\\scripts\\quick_export.ps1")
        return
    
    print(f"Loading poses from {poses_file}...")
    poses = load_raw_poses(poses_file)
    
    if len(poses) == 0:
        print("No poses found in file!")
        return
    
    print(f"✓ Loaded {len(poses)} pose frames")
    print(f"✓ Session ID: {poses[0]['session_id']}")
    print(f"✓ Frame range: {poses[0]['frame_number']} - {poses[-1]['frame_number']}")
    print()
    
    # Analyze right wrist motion
    trajectory, velocities = analyze_stroke_motion(poses, landmark_index=16)
    
    # Plot first frame skeleton
    print("\nPlotting first frame skeleton...")
    plot_skeleton(poses[0], title=f"Frame {poses[0]['frame_number']}")
    
    # Plot wrist trajectory 3D
    if len(poses) > 1:
        print("Plotting 3D wrist trajectory...")
        plot_wrist_trajectory_3d(poses)
        
        # Compare shoulder, elbow, wrist
        print("Comparing right arm landmarks...")
        compare_landmarks(
            poses,
            landmark_indices=[12, 14, 16],
            labels=['Right Shoulder', 'Right Elbow', 'Right Wrist']
        )
    
    print("\n✓ Analysis complete!")

if __name__ == '__main__':
    main()

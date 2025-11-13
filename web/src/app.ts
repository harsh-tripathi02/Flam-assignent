/**
 * Frame data interface
 */
interface FrameData {
    image: string;        // Base64 encoded image
    fps: number;          // Frames per second
    resolution: string;   // Resolution (e.g., "640x480")
    mode: string;         // Processing mode
    timestamp: number;    // Unix timestamp
}

/**
 * OpenCV Frame Viewer Application
 */
class FrameViewer {
    private frameImage: HTMLImageElement;
    private fpsElement: HTMLElement;
    private resolutionElement: HTMLElement;
    private modeElement: HTMLElement;
    private timestampElement: HTMLElement;

    constructor() {
        // Get DOM elements
        this.frameImage = document.getElementById('frameImage') as HTMLImageElement;
        this.fpsElement = document.getElementById('fpsValue') as HTMLElement;
        this.resolutionElement = document.getElementById('resolutionValue') as HTMLElement;
        this.modeElement = document.getElementById('modeValue') as HTMLElement;
        this.timestampElement = document.getElementById('timestamp') as HTMLElement;

        // Initialize with sample data
        this.init();
    }

    /**
     * Initialize the viewer with sample data
     */
    private init(): void {
        console.log('Initializing Frame Viewer...');

        // Sample processed frame (Canny edge detection result)
        // This is a placeholder - in production, this would come from the Android app
        const sampleFrame: FrameData = {
            image: this.generateSampleFrame(),
            fps: 15.2,
            resolution: '640x480',
            mode: 'Canny Edge',
            timestamp: Date.now()
        };

        this.updateFrame(sampleFrame);
        this.updateTimestamp();

        console.log('Frame Viewer initialized successfully');
    }

    /**
     * Update the displayed frame
     */
    public updateFrame(data: FrameData): void {
        // Update image
        this.frameImage.src = data.image;

        // Update stats
        this.fpsElement.textContent = data.fps.toFixed(1);
        this.resolutionElement.textContent = data.resolution;
        this.modeElement.textContent = data.mode;

        console.log(`Frame updated: ${data.resolution}, FPS: ${data.fps}, Mode: ${data.mode}`);
    }

    /**
     * Update timestamp
     */
    private updateTimestamp(): void {
        const now = new Date();
        const timeString = now.toLocaleString('en-US', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
        this.timestampElement.textContent = `Last updated: ${timeString}`;

        // Update every second
        setTimeout(() => this.updateTimestamp(), 1000);
    }

    /**
     * Generate a sample frame (placeholder)
     * In production, this would be replaced with actual base64 image from Android
     */
    private generateSampleFrame(): string {
        // This is a 1x1 pixel transparent PNG as placeholder
        // Replace with actual processed frame from Android app
        const canvas = document.createElement('canvas');
        canvas.width = 640;
        canvas.height = 480;
        const ctx = canvas.getContext('2d');

        if (ctx) {
            // Draw gradient background
            const gradient = ctx.createLinearGradient(0, 0, 640, 480);
            gradient.addColorStop(0, '#1a1a1a');
            gradient.addColorStop(1, '#2d2d2d');
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, 640, 480);

            // Draw sample edge detection pattern
            ctx.strokeStyle = '#00ff00';
            ctx.lineWidth = 2;

            // Draw some edges
            for (let i = 0; i < 20; i++) {
                ctx.beginPath();
                const x1 = Math.random() * 640;
                const y1 = Math.random() * 480;
                const x2 = x1 + (Math.random() - 0.5) * 200;
                const y2 = y1 + (Math.random() - 0.5) * 200;
                ctx.moveTo(x1, y1);
                ctx.lineTo(x2, y2);
                ctx.stroke();
            }

            // Add text
            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 24px Arial';
            ctx.textAlign = 'center';
            ctx.fillText('Sample Edge Detection Output', 320, 240);
            ctx.font = '16px Arial';
            ctx.fillText('Replace with actual processed frame from Android', 320, 270);
        }

        return canvas.toDataURL('image/png');
    }

    /**
     * Load frame from base64 string
     * This method can be called from external sources (e.g., WebSocket, HTTP)
     */
    public loadFrameFromBase64(base64: string, fps: number, resolution: string, mode: string): void {
        const data: FrameData = {
            image: base64.startsWith('data:') ? base64 : `data:image/png;base64,${base64}`,
            fps: fps,
            resolution: resolution,
            mode: mode,
            timestamp: Date.now()
        };

        this.updateFrame(data);
    }
}

// Initialize viewer when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    const viewer = new FrameViewer();

    // Expose viewer to global scope for external access
    (window as any).frameViewer = viewer;

    console.log('Frame Viewer ready. Use window.frameViewer.loadFrameFromBase64() to update frames.');
});


"""
PDF Text Extraction Module
Handles extraction of text content from PDF files
"""
from pathlib import Path
from pypdf.errors import PdfReadError
import pypdf


def extract_text_from_pdf(pdf_path: Path) -> str:
    """Extract text content from a PDF file.
    
    Args:
        pdf_path: Path to the PDF file
        
    Returns:
        Extracted text as a string
        
    Raises:
        Exception: If PDF extraction fails
    """
    try:
        with open(pdf_path, 'rb') as file:
            pdf_reader = pypdf.PdfReader(file)
            
            # Check if PDF is encrypted
            if pdf_reader.is_encrypted:
                raise Exception("PDF is encrypted. Cannot extract text from encrypted PDFs.")
            
            # Extract text from all pages
            text = ""
            num_pages = len(pdf_reader.pages)
            
            for page_num, page in enumerate(pdf_reader.pages):
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
            
            # Clean up the text
            text = text.strip()
            
            if not text:
                raise Exception("No text could be extracted from the PDF. The PDF might be image-based or empty.")
            
            return text
    
    except FileNotFoundError:
        raise Exception(f"PDF file not found: {pdf_path}")
    except PdfReadError as e:
        raise Exception(f"Failed to read PDF file: {e}")
    except Exception as e:
        raise Exception(f"Failed to extract text from PDF: {e}")


def extract_text_with_metadata(pdf_path: Path) -> dict:
    """Extract text and metadata from a PDF file.
    
    Args:
        pdf_path: Path to the PDF file
        
    Returns:
        Dictionary with text content and metadata
        
    Raises:
        Exception: If PDF extraction fails
    """
    try:
        with open(pdf_path, 'rb') as file:
            pdf_reader = pypdf.PdfReader(file)
            
            # Check if PDF is encrypted
            if pdf_reader.is_encrypted:
                raise Exception("PDF is encrypted. Cannot extract text from encrypted PDFs.")
            
            # Extract text
            text = ""
            for page in pdf_reader.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
            
            text = text.strip()
            
            # Extract metadata
            metadata = pdf_reader.metadata if pdf_reader.metadata else {}
            
            # Build response
            result = {
                "text": text,
                "num_pages": len(pdf_reader.pages),
                "file_name": pdf_path.name,
                "file_size_bytes": pdf_path.stat().st_size,
                "metadata": {
                    "title": metadata.get('/Title', '') if metadata else '',
                    "author": metadata.get('/Author', '') if metadata else '',
                    "subject": metadata.get('/Subject', '') if metadata else '',
                    "creator": metadata.get('/Creator', '') if metadata else '',
                    "producer": metadata.get('/Producer', '') if metadata else '',
                    "creation_date": metadata.get('/CreationDate', '') if metadata else '',
                }
            }
            
            return result
    
    except FileNotFoundError:
        raise Exception(f"PDF file not found: {pdf_path}")
    except PdfReadError as e:
        raise Exception(f"Failed to read PDF file: {e}")
    except Exception as e:
        raise Exception(f"Failed to extract text from PDF: {e}")


def validate_pdf(pdf_path: Path) -> tuple[bool, str]:
    """Validate if a file is a valid PDF and can be read.
    
    Args:
        pdf_path: Path to the PDF file
        
    Returns:
        Tuple of (is_valid, error_message)
    """
    try:
        # Check if file exists
        if not pdf_path.exists():
            return False, f"File does not exist: {pdf_path}"
        
        # Check file extension
        if pdf_path.suffix.lower() != '.pdf':
            return False, f"File is not a PDF: {pdf_path.suffix}"
        
        # Check file size
        file_size = pdf_path.stat().st_size
        if file_size == 0:
            return False, "PDF file is empty (0 bytes)"
        
        # Try to open and read PDF
        with open(pdf_path, 'rb') as file:
            pdf_reader = pypdf.PdfReader(file)
            
            # Check if encrypted
            if pdf_reader.is_encrypted:
                return False, "PDF is encrypted"
            
            # Check if has pages
            if len(pdf_reader.pages) == 0:
                return False, "PDF has no pages"
            
            # Try to extract text from first page
            first_page = pdf_reader.pages[0]
            first_page.extract_text()
        
        return True, "PDF is valid"
    
    except PdfReadError as e:
        return False, f"Invalid PDF format: {e}"
    except Exception as e:
        return False, f"PDF validation error: {e}"


def extract_text_by_page(pdf_path: Path) -> list[dict]:
    """Extract text from PDF with page-by-page breakdown.
    
    Args:
        pdf_path: Path to the PDF file
        
    Returns:
        List of dictionaries with page number and text content
        
    Raises:
        Exception: If PDF extraction fails
    """
    try:
        with open(pdf_path, 'rb') as file:
            pdf_reader = pypdf.PdfReader(file)
            
            if pdf_reader.is_encrypted:
                raise Exception("PDF is encrypted")
            
            pages_data = []
            for page_num, page in enumerate(pdf_reader.pages, start=1):
                page_text = page.extract_text()
                pages_data.append({
                    "page_number": page_num,
                    "text": page_text.strip() if page_text else "",
                    "char_count": len(page_text) if page_text else 0
                })
            
            return pages_data
    
    except FileNotFoundError:
        raise Exception(f"PDF file not found: {pdf_path}")
    except PdfReadError as e:
        raise Exception(f"Failed to read PDF file: {e}")
    except Exception as e:
        raise Exception(f"Failed to extract text from PDF: {e}")


# Utility function for debugging
def print_pdf_info(pdf_path: Path):
    """Print detailed information about a PDF file.
    
    Args:
        pdf_path: Path to the PDF file
    """
    try:
        print(f"\n{'=' * 60}")
        print(f"PDF Information: {pdf_path.name}")
        print(f"{'=' * 60}")
        
        # Validate first
        is_valid, message = validate_pdf(pdf_path)
        print(f"Valid: {is_valid}")
        if not is_valid:
            print(f"Error: {message}")
            return
        
        # Extract with metadata
        result = extract_text_with_metadata(pdf_path)
        
        print(f"\nFile Details:")
        print(f"  - File Name: {result['file_name']}")
        print(f"  - File Size: {result['file_size_bytes']:,} bytes")
        print(f"  - Number of Pages: {result['num_pages']}")
        print(f"  - Text Length: {len(result['text']):,} characters")
        
        if any(result['metadata'].values()):
            print(f"\nMetadata:")
            for key, value in result['metadata'].items():
                if value:
                    print(f"  - {key.title()}: {value}")
        
        print(f"\nFirst 200 characters of text:")
        print(f"{'-' * 60}")
        print(result['text'][:200])
        print(f"{'-' * 60}")
        print()
        
    except Exception as e:
        print(f"Error: {e}")


# Example usage and testing
if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1:
        # Test with provided PDF path
        pdf_path = Path(sys.argv[1])
        print_pdf_info(pdf_path)
    else:
        print("Usage: python pdf_extractor.py <path_to_pdf>")
        print("\nExample:")
        print("  python pdf_extractor.py ../input/7391052846.pdf")